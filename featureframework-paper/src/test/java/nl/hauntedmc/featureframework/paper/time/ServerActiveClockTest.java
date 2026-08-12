package nl.hauntedmc.featureframework.paper.time;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import java.nio.file.Path;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ServerActiveClockTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void nowMillisDoesNotWaitForLifecycleMonitor() throws Exception {
        ServerActiveClock clock = new ServerActiveClock(plugin(), () -> 0L);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch monitorHeld = new CountDownLatch(1);
        CountDownLatch releaseMonitor = new CountDownLatch(1);

        try {
            Future<?> holder = executor.submit(() -> {
                synchronized (clock) {
                    monitorHeld.countDown();
                    await(releaseMonitor);
                }
            });
            assertTrue(monitorHeld.await(1, TimeUnit.SECONDS));

            Future<Long> read = executor.submit(clock::nowMillis);

            assertEquals(0L, read.get(1, TimeUnit.SECONDS).longValue());
            releaseMonitor.countDown();
            holder.get(1, TimeUnit.SECONDS);
        } finally {
            releaseMonitor.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void persistsActiveTimeWithoutCountingTimeBetweenFeatureSessions() throws Exception {
        Plugin plugin = plugin();
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        BukkitTask firstTask = mock(BukkitTask.class);
        BukkitTask secondTask = mock(BukkitTask.class);
        when(scheduler.runTaskTimerAsynchronously(
                eq(plugin), any(Runnable.class), eq(100L), eq(100L)
        )).thenReturn(firstTask, secondTask);
        AtomicLong nanos = new AtomicLong(1_000_000_000L);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            ServerActiveClock firstSession = new ServerActiveClock(plugin, nanos::get);
            firstSession.start();
            nanos.addAndGet(TimeUnit.MILLISECONDS.toNanos(250L));

            firstSession.checkpoint();
            nanos.addAndGet(TimeUnit.MILLISECONDS.toNanos(75L));
            assertEquals(325L, firstSession.nowMillis());
            firstSession.close();

            nanos.addAndGet(TimeUnit.HOURS.toNanos(12L));
            ServerActiveClock secondSession = new ServerActiveClock(plugin, nanos::get);
            secondSession.start();
            assertEquals(325L, secondSession.nowMillis());
            nanos.addAndGet(TimeUnit.MILLISECONDS.toNanos(125L));
            assertEquals(450L, secondSession.nowMillis());
            secondSession.close();

            verify(firstTask).cancel();
            verify(secondTask).cancel();
        }
    }

    @Test
    void closedClockCannotBeRestarted() {
        ServerActiveClock clock = new ServerActiveClock(plugin(), () -> 0L);

        clock.close();

        assertThrows(IllegalStateException.class, clock::start);
    }

    @Test
    void closingAnUnstartedClockDoesNotOverwriteAnExistingCheckpoint() throws Exception {
        Plugin plugin = plugin();
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        BukkitTask task = mock(BukkitTask.class);
        when(scheduler.runTaskTimerAsynchronously(
                eq(plugin), any(Runnable.class), eq(100L), eq(100L)
        )).thenReturn(task);
        AtomicLong nanos = new AtomicLong(1_000_000_000L);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            ServerActiveClock writer = new ServerActiveClock(plugin, nanos::get);
            writer.start();
            nanos.addAndGet(TimeUnit.SECONDS.toNanos(2L));
            writer.close();

            new ServerActiveClock(plugin, nanos::get).close();

            ServerActiveClock reader = new ServerActiveClock(plugin, nanos::get);
            reader.start();
            assertEquals(2_000L, reader.nowMillis());
            reader.close();
        }
    }

    private Plugin plugin() {
        Plugin plugin = mock(Plugin.class);
        when(plugin.getDataFolder()).thenReturn(temporaryDirectory.toFile());
        return plugin;
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }
}
