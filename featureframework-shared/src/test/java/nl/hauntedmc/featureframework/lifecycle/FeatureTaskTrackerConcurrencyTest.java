package nl.hauntedmc.featureframework.lifecycle;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeatureTaskTrackerConcurrencyTest {
    @Test
    void quiesceDuringNativeSubmissionCancelsTheSubmittedHandle() throws Exception {
        FeatureTaskTracker<Handle> tracker = new FeatureTaskTracker<>();
        Handle handle = new Handle();
        CountDownLatch submitting = new CountDownLatch(1);
        CountDownLatch releaseSubmission = new CountDownLatch(1);

        CompletableFuture<Throwable> result = CompletableFuture.supplyAsync(() -> {
            try {
                tracker.scheduleRepeating(ignored -> {
                    submitting.countDown();
                    await(releaseSubmission);
                    return handle;
                }, () -> { }, Handle::cancel);
                return null;
            } catch (Throwable failure) {
                return failure;
            }
        });

        assertTrue(submitting.await(5, TimeUnit.SECONDS), "native submission did not start");
        tracker.quiesce();
        releaseSubmission.countDown();

        Throwable failure = result.get(5, TimeUnit.SECONDS);
        assertInstanceOf(IllegalStateException.class, failure);
        assertTrue(handle.cancelled.get(), "handle submitted during quiescing must be cancelled");
        assertEquals(0, tracker.activeCount());
        assertEquals(FeatureResourceState.QUIESCING, tracker.state());
    }

    @Test
    void cancelAllAggregatesNativeCancellationFailuresWithoutClaimingClosed() {
        FeatureTaskTracker<Handle> tracker = new FeatureTaskTracker<>();
        Handle firstHandle = tracker.scheduleRepeating(ignored -> new Handle(), () -> { }, Handle::cancel);
        Handle secondHandle = tracker.scheduleRepeating(ignored -> new Handle(), () -> { }, Handle::cancel);
        RuntimeException first = new RuntimeException("first cancellation failed");
        RuntimeException second = new RuntimeException("second cancellation failed");

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> tracker.cancelAll(handle -> {
            if (handle == firstHandle) throw first;
            if (handle == secondHandle) throw second;
        }, Duration.ZERO));

        assertSame(first, thrown);
        assertEquals(1, thrown.getSuppressed().length);
        assertSame(second, thrown.getSuppressed()[0]);
        assertEquals(2, tracker.activeCount(), "failed native cancellations must remain tracked");
        assertEquals(FeatureResourceState.QUIESCING, tracker.state());
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) throw new AssertionError("timed out awaiting test latch");
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrupted awaiting test latch", failure);
        }
    }

    private static final class Handle {
        private final AtomicBoolean cancelled = new AtomicBoolean();

        void cancel() {
            cancelled.set(true);
        }
    }
}
