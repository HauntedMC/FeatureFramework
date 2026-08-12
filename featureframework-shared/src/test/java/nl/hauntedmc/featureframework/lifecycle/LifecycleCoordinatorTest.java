package nl.hauntedmc.featureframework.lifecycle;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class LifecycleCoordinatorTest {

    @Test
    void serializesConcurrentFeatureGraphMutations() throws Exception {
        LifecycleCoordinator coordinator = new LifecycleCoordinator();
        CountDownLatch firstEntered = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondEntered = new CountDownLatch(1);

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(() -> coordinator.runExclusive(() -> {
                firstEntered.countDown();
                await(releaseFirst);
            }));
            assertTrue(firstEntered.await(5, TimeUnit.SECONDS));

            var second = executor.submit(() -> coordinator.runExclusive(secondEntered::countDown));
            assertFalse(secondEntered.await(100, TimeUnit.MILLISECONDS));

            releaseFirst.countDown();
            first.get();
            second.get();
            assertEquals(0, secondEntered.getCount());
        }
    }

    @Test
    void supportsNestedLifecycleOperationsOnTheSameThread() {
        LifecycleCoordinator coordinator = new LifecycleCoordinator();

        assertTimeoutPreemptively(Duration.ofSeconds(1),
                () -> coordinator.runExclusive(() -> coordinator.runExclusive(() -> { })));
    }

    @Test
    void rejectsNullOperations() {
        LifecycleCoordinator coordinator = new LifecycleCoordinator();

        assertThrows(NullPointerException.class, () -> coordinator.runExclusive(null));
        assertThrows(NullPointerException.class, () -> coordinator.callExclusive(null));
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while coordinating test operations", interrupted);
        }
    }
}
