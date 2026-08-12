package nl.hauntedmc.featureframework.paper.lifecycle;

import nl.hauntedmc.featureframework.lifecycle.FeatureOperationExecutor;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.function.Supplier;

/** Executes synchronous feature lifecycle work on Paper's primary server thread. */
public final class PaperFeatureOperationExecutor implements FeatureOperationExecutor {
    private final Plugin plugin;

    public PaperFeatureOperationExecutor(Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    @Override
    public void run(Runnable operation) {
        call(() -> {
            Objects.requireNonNull(operation, "operation").run();
            return null;
        });
    }

    @Override
    public <T> T call(Supplier<T> operation) {
        Objects.requireNonNull(operation, "operation");
        if (Bukkit.isPrimaryThread()) {
            return operation.get();
        }

        FutureTask<T> task = new FutureTask<>(operation::get);
        Bukkit.getScheduler().runTask(plugin, task);
        try {
            return task.get();
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for Paper's primary thread", failure);
        } catch (ExecutionException failure) {
            throwUnchecked(failure.getCause());
            throw new AssertionError("unreachable");
        }
    }

    @SuppressWarnings("unchecked")
    private static <E extends Throwable> void throwUnchecked(Throwable failure) throws E {
        throw (E) failure;
    }
}
