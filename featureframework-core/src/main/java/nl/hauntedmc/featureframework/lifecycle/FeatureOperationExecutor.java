package nl.hauntedmc.featureframework.lifecycle;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Executes synchronous feature lifecycle operations on the thread required by a platform.
 *
 * <p>The shared framework defaults to direct execution. Platform adapters may bind an executor before
 * a host starts when their API imposes an execution-affinity requirement, such as Paper's primary
 * server thread.</p>
 */
public interface FeatureOperationExecutor {
    void run(Runnable operation);

    <T> T call(Supplier<T> operation);

    static FeatureOperationExecutor direct() {
        return Direct.INSTANCE;
    }

    enum Direct implements FeatureOperationExecutor {
        INSTANCE;

        @Override
        public void run(Runnable operation) {
            Objects.requireNonNull(operation, "operation").run();
        }

        @Override
        public <T> T call(Supplier<T> operation) {
            return Objects.requireNonNull(operation, "operation").get();
        }
    }
}
