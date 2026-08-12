package nl.hauntedmc.featureframework.lifecycle;

import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/** Serializes feature-graph mutations and runs them with the platform's required execution affinity. */
public final class LifecycleCoordinator {
    private final ReentrantLock operationLock = new ReentrantLock(true);
    private volatile FeatureOperationExecutor executor = FeatureOperationExecutor.direct();

    /**
     * Binds the executor used by subsequent lifecycle operations.
     *
     * <p>This is intended for platform composition before the host starts. The executor is entered
     * before the graph lock is acquired, preventing a caller thread from holding the lifecycle lock
     * while waiting for a platform thread.</p>
     */
    public void bindExecutor(FeatureOperationExecutor executor) {
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    public void runExclusive(Runnable operation) {
        Objects.requireNonNull(operation, "operation");
        executor.run(() -> callLocked(() -> {
            operation.run();
            return null;
        }));
    }

    public <T> T callExclusive(Supplier<T> operation) {
        Objects.requireNonNull(operation, "operation");
        return executor.call(() -> callLocked(operation));
    }

    private <T> T callLocked(Supplier<T> operation) {
        operationLock.lock();
        try {
            return operation.get();
        } finally {
            operationLock.unlock();
        }
    }
}
