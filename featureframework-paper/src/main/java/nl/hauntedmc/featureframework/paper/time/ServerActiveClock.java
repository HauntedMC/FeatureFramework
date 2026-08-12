package nl.hauntedmc.featureframework.paper.time;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.Objects;
import java.util.function.LongSupplier;
import java.util.logging.Level;

/**
 * Persistent monotonic clock that advances only while its owning feature is active.
 *
 * <p>The checkpoint intentionally excludes unflushed crash time. Consumers can therefore live a few
 * seconds longer after a hard crash, but can never expire early because wall-clock time advanced while
 * the backend was offline.</p>
 */
public final class ServerActiveClock implements AutoCloseable {
    private static final long CHECKPOINT_PERIOD_TICKS = 100L;
    private static final int CHECKPOINT_BYTES = Long.BYTES;

    private final Plugin plugin;
    private final Path checkpointPath;
    private final LongSupplier nanoTime;

    private volatile ActiveTimeState state = ActiveTimeState.stopped(0L);
    private volatile BukkitTask checkpointTask;
    private boolean started;
    private boolean closed;

    public ServerActiveClock(Plugin plugin) {
        this(plugin, System::nanoTime);
    }

    ServerActiveClock(Plugin plugin, LongSupplier nanoTime) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime");
        this.checkpointPath = plugin.getDataFolder().toPath()
                .resolve("runtime")
                .resolve("server-active-clock.bin");
    }

    public synchronized void start() {
        if (state.running()) {
            return;
        }
        if (closed) {
            throw new IllegalStateException("A closed active clock cannot be restarted");
        }
        state = ActiveTimeState.running(readCheckpoint(), nanoTime.getAsLong());
        started = true;
        checkpointTask = Bukkit.getScheduler().runTaskTimerAsynchronously(
                plugin,
                this::checkpointSafely,
                CHECKPOINT_PERIOD_TICKS,
                CHECKPOINT_PERIOD_TICKS
        );
    }

    /**
     * Returns a coherent active-time snapshot without acquiring the checkpoint lifecycle monitor.
     */
    public long nowMillis() {
        return state.atNanos(nanoTime.getAsLong());
    }

    public synchronized void checkpoint() throws IOException {
        ActiveTimeState snapshot = state;
        if (closed || !snapshot.running()) {
            return;
        }
        long checkpointNanos = nanoTime.getAsLong();
        long current = snapshot.atNanos(checkpointNanos);
        state = ActiveTimeState.running(current, checkpointNanos);
        writeCheckpoint(current);
    }

    private long readCheckpoint() {
        try {
            if (!Files.isRegularFile(checkpointPath)) {
                return 0L;
            }
            byte[] bytes = Files.readAllBytes(checkpointPath);
            if (bytes.length != CHECKPOINT_BYTES) {
                throw new IOException("Unexpected active-clock checkpoint length: " + bytes.length);
            }
            return Math.max(0L, ByteBuffer.wrap(bytes).getLong());
        } catch (IOException exception) {
            plugin.getLogger().log(
                    Level.WARNING,
                    "Could not read the server active-clock checkpoint; starting at zero.",
                    exception
            );
            return 0L;
        }
    }

    private void writeCheckpoint(long value) throws IOException {
        Files.createDirectories(checkpointPath.getParent());
        Path temporary = checkpointPath.resolveSibling(checkpointPath.getFileName() + ".tmp");
        byte[] bytes = ByteBuffer.allocate(CHECKPOINT_BYTES).putLong(value).array();
        try (FileChannel channel = FileChannel.open(
                temporary,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE
        )) {
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
            channel.force(true);
        }
        try {
            Files.move(
                    temporary,
                    checkpointPath,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
            );
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporary, checkpointPath, StandardCopyOption.REPLACE_EXISTING);
        }
        forceDirectory(checkpointPath.getParent());
    }

    private static void forceDirectory(Path directory) throws IOException {
        try (FileChannel channel = FileChannel.open(directory, StandardOpenOption.READ)) {
            channel.force(true);
        } catch (UnsupportedOperationException ignored) {
            // Some filesystems do not expose directory fsync through FileChannel.
        }
    }

    private void checkpointSafely() {
        try {
            checkpoint();
        } catch (IOException exception) {
            plugin.getLogger().log(Level.WARNING, "Could not checkpoint the server active clock.", exception);
        }
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        BukkitTask task = checkpointTask;
        checkpointTask = null;
        if (task != null) {
            task.cancel();
        }
        if (!started) {
            return;
        }
        ActiveTimeState snapshot = state;
        long current = snapshot.atNanos(nanoTime.getAsLong());
        state = ActiveTimeState.stopped(current);
        try {
            writeCheckpoint(current);
        } catch (IOException exception) {
            plugin.getLogger().log(Level.SEVERE, "Could not write the final server active-clock checkpoint.", exception);
        }
    }

    private record ActiveTimeState(long accumulatedMillis, long sessionStartedNanos, boolean running) {
        private static ActiveTimeState running(long accumulatedMillis, long sessionStartedNanos) {
            return new ActiveTimeState(accumulatedMillis, sessionStartedNanos, true);
        }

        private static ActiveTimeState stopped(long accumulatedMillis) {
            return new ActiveTimeState(accumulatedMillis, 0L, false);
        }

        private long atNanos(long currentNanos) {
            if (!running) {
                return accumulatedMillis;
            }
            long elapsedNanos = Math.max(0L, currentNanos - sessionStartedNanos);
            return Math.addExact(accumulatedMillis, elapsedNanos / 1_000_000L);
        }
    }
}
