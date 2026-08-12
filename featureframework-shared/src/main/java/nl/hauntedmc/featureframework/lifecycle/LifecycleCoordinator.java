package nl.hauntedmc.featureframework.lifecycle;

import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/** Serializes mutations to a feature graph for the lifetime of its host runtime. */
public final class LifecycleCoordinator {
    private final ReentrantLock operationLock = new ReentrantLock(true);

    public void runExclusive(Runnable operation) {
        Objects.requireNonNull(operation, "operation");
        callExclusive(() -> {
            operation.run();
            return null;
        });
    }

    public <T> T callExclusive(Supplier<T> operation) {
        Objects.requireNonNull(operation, "operation");
        operationLock.lock();
        try {
            return operation.get();
        } finally {
            operationLock.unlock();
        }
    }
}
