package nl.hauntedmc.featureframework.lifecycle;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
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

        Set<Throwable> observed = new HashSet<>();
        observed.add(thrown);
        observed.addAll(Arrays.asList(thrown.getSuppressed()));
        assertEquals(Set.of(first, second), observed);
        assertEquals(2, tracker.activeCount(), "failed native cancellations must remain tracked");
        assertEquals(FeatureResourceState.QUIESCING, tracker.state());
    }

    @Test
    void cancelAllTimesOutWhileCallbackIsInFlightAndCanCloseAfterDrain() throws Exception {
        FeatureTaskTracker<Handle> tracker = new FeatureTaskTracker<>();
        AtomicReference<Runnable> scheduled = new AtomicReference<>();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Handle handle = tracker.scheduleRepeating(runnable -> {
            scheduled.set(runnable);
            return new Handle();
        }, () -> {
            entered.countDown();
            await(release);
        }, Handle::cancel);

        Thread callback = Thread.ofVirtual().start(scheduled.get());
        assertTrue(entered.await(5, TimeUnit.SECONDS), "tracked callback did not start");
        try {
            IllegalStateException timeout = assertThrows(IllegalStateException.class,
                    () -> tracker.cancelAll(Handle::cancel, Duration.ofMillis(10)));
            assertTrue(timeout.getMessage().startsWith("Timed out waiting for "));
            assertTrue(timeout.getMessage().contains("feature task callback"));
            assertTrue(handle.cancelled.get());
            assertEquals(0, tracker.activeCount());
            assertEquals(1, tracker.inFlightCount());
            assertEquals(FeatureResourceState.QUIESCING, tracker.state());
        } finally {
            release.countDown();
        }

        callback.join(5_000L);
        assertFalse(callback.isAlive(), "tracked callback did not finish");
        assertEquals(0, tracker.inFlightCount());

        tracker.cancelAll(Handle::cancel, Duration.ZERO);
        assertEquals(FeatureResourceState.CLOSED, tracker.state());
    }

    @Test
    void cancelAllDoesNotLoseInterruptedStatusWhileDraining() throws Exception {
        FeatureTaskTracker<Handle> tracker = new FeatureTaskTracker<>();
        AtomicReference<Runnable> scheduled = new AtomicReference<>();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        tracker.scheduleRepeating(runnable -> {
            scheduled.set(runnable);
            return new Handle();
        }, () -> {
            entered.countDown();
            await(release);
        }, Handle::cancel);

        Thread callback = Thread.ofVirtual().start(scheduled.get());
        assertTrue(entered.await(5, TimeUnit.SECONDS), "tracked callback did not start");

        AtomicBoolean interruptedAfterDrain = new AtomicBoolean();
        AtomicReference<Throwable> workerFailure = new AtomicReference<>();
        CountDownLatch workerStarted = new CountDownLatch(1);
        Thread worker = Thread.ofVirtual().start(() -> {
            workerStarted.countDown();
            try {
                tracker.cancelAll(Handle::cancel, Duration.ofSeconds(5));
                interruptedAfterDrain.set(Thread.currentThread().isInterrupted());
            } catch (Throwable failure) {
                workerFailure.set(failure);
            }
        });

        assertTrue(workerStarted.await(5, TimeUnit.SECONDS), "drain worker did not start");
        worker.interrupt();
        release.countDown();

        worker.join(5_000L);
        callback.join(5_000L);
        assertFalse(worker.isAlive(), "drain worker did not finish");
        assertFalse(callback.isAlive(), "tracked callback did not finish");
        assertNull(workerFailure.get());
        assertTrue(interruptedAfterDrain.get(), "drain must restore the worker interrupted status");
        assertEquals(FeatureResourceState.CLOSED, tracker.state());
        assertEquals(0, tracker.inFlightCount());
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
