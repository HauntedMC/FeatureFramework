package nl.hauntedmc.featureframework.lifecycle;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeatureLifecycleInfrastructureTest {
    @Test
    void lifecycleCoordinatorEntersExecutorBeforeOperation() {
        LifecycleCoordinator coordinator = new LifecycleCoordinator();
        AtomicBoolean insideExecutor = new AtomicBoolean();
        AtomicBoolean operationObservedExecutor = new AtomicBoolean();
        coordinator.bindExecutor(new FeatureOperationExecutor() {
            @Override
            public void run(Runnable operation) {
                insideExecutor.set(true);
                try {
                    operation.run();
                } finally {
                    insideExecutor.set(false);
                }
            }

            @Override
            public <T> T call(java.util.function.Supplier<T> operation) {
                insideExecutor.set(true);
                try {
                    return operation.get();
                } finally {
                    insideExecutor.set(false);
                }
            }
        });

        coordinator.runExclusive(() -> operationObservedExecutor.set(insideExecutor.get()));

        assertTrue(operationObservedExecutor.get());
    }

    @Test
    void oneShotTaskCompletingDuringSubmissionDoesNotLeakHandle() {
        FeatureTaskTracker<Handle> tracker = new FeatureTaskTracker<>();
        AtomicInteger runs = new AtomicInteger();

        tracker.scheduleOnce(runnable -> {
            Handle handle = new Handle();
            runnable.run();
            return handle;
        }, runs::incrementAndGet, Handle::cancel);

        assertEquals(1, runs.get());
        assertEquals(0, tracker.activeCount());
    }

    @Test
    void quiescingRejectsNewTasksAndCleanupClosesTracker() {
        FeatureTaskTracker<Handle> tracker = new FeatureTaskTracker<>();
        Handle repeating = tracker.scheduleRepeating(
                runnable -> new Handle(), () -> { }, Handle::cancel);

        assertEquals(1, tracker.activeCount());
        tracker.quiesce();
        assertThrows(IllegalStateException.class, () -> tracker.scheduleRepeating(
                runnable -> new Handle(), () -> { }, Handle::cancel));

        tracker.cancelAll(Handle::cancel);

        assertTrue(repeating.cancelled.get());
        assertEquals(0, tracker.activeCount());
        assertEquals(FeatureResourceState.CLOSED, tracker.state());
    }

    private static final class Handle {
        private final AtomicBoolean cancelled = new AtomicBoolean();
        void cancel() { cancelled.set(true); }
    }
}
