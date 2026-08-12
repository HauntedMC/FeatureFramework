package nl.hauntedmc.featureframework.lifecycle;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Platform-neutral ownership and shutdown state machine for scheduled task handles.
 *
 * <p>Platform adapters provide only native submission and cancellation. Registration races,
 * one-shot removal, quiescing, in-flight tracking and bounded draining are kept identical across
 * Paper and Velocity.</p>
 */
public final class FeatureTaskTracker<H> {
    private static final Duration DEFAULT_DRAIN_TIMEOUT = Duration.ofSeconds(30);

    private final Set<H> active = ConcurrentHashMap.newKeySet();
    private final Object registrationLock = new Object();
    private final AtomicReference<FeatureResourceState> state =
            new AtomicReference<>(FeatureResourceState.OPEN);
    private final AtomicInteger inFlight = new AtomicInteger();
    private final ReentrantLock drainLock = new ReentrantLock();
    private final Condition drained = drainLock.newCondition();

    public H scheduleOnce(Function<Runnable, H> submitter, Runnable task, Consumer<H> cancellation) {
        AtomicReference<H> handleRef = new AtomicReference<>();
        AtomicBoolean completed = new AtomicBoolean();
        Runnable wrapped = () -> {
            try {
                runTracked(task);
            } finally {
                completed.set(true);
                H handle = handleRef.get();
                if (handle != null) active.remove(handle);
            }
        };

        synchronized (registrationLock) {
            requireOpen();
            H handle = submitter.apply(wrapped);
            if (state.get() != FeatureResourceState.OPEN) {
                cancellation.accept(handle);
                throw new IllegalStateException("Task tracker began quiescing while scheduling a task");
            }
            handleRef.set(handle);
            active.add(handle);
            if (completed.get()) active.remove(handle);
            return handle;
        }
    }

    public H scheduleRepeating(Function<Runnable, H> submitter, Runnable task, Consumer<H> cancellation) {
        synchronized (registrationLock) {
            requireOpen();
            H handle = submitter.apply(() -> runTracked(task));
            if (state.get() != FeatureResourceState.OPEN) {
                cancellation.accept(handle);
                throw new IllegalStateException("Task tracker began quiescing while scheduling a task");
            }
            active.add(handle);
            return handle;
        }
    }

    public void cancel(H handle, Consumer<H> cancellation) {
        if (handle == null) return;
        synchronized (registrationLock) {
            cancellation.accept(handle);
            active.remove(handle);
        }
    }

    public void quiesce() {
        state.compareAndSet(FeatureResourceState.OPEN, FeatureResourceState.QUIESCING);
    }

    public void cancelAll(Consumer<H> cancellation) {
        cancelAll(cancellation, DEFAULT_DRAIN_TIMEOUT);
    }

    public void cancelAll(Consumer<H> cancellation, Duration timeout) {
        quiesce();
        Throwable failure = null;
        synchronized (registrationLock) {
            for (H handle : new ArrayList<>(active)) {
                try {
                    cancellation.accept(handle);
                    active.remove(handle);
                } catch (Throwable stepFailure) {
                    failure = appendFailure(failure, stepFailure);
                }
            }
        }
        try {
            awaitDrain(timeout);
        } catch (Throwable drainFailure) {
            failure = appendFailure(failure, drainFailure);
        }
        if (failure == null && active.isEmpty()) state.set(FeatureResourceState.CLOSED);
        if (failure != null) throwUnchecked(failure);
    }

    public FeatureResourceState state() { return state.get(); }
    public int activeCount() { return active.size(); }
    public int inFlightCount() { return inFlight.get(); }

    private void runTracked(Runnable task) {
        inFlight.incrementAndGet();
        try {
            task.run();
        } finally {
            if (inFlight.decrementAndGet() == 0) {
                drainLock.lock();
                try {
                    drained.signalAll();
                } finally {
                    drainLock.unlock();
                }
            }
        }
    }

    private void awaitDrain(Duration timeout) {
        long remaining = timeout.toNanos();
        boolean interrupted = false;
        drainLock.lock();
        try {
            while (inFlight.get() > 0 && remaining > 0L) {
                long started = System.nanoTime();
                try {
                    remaining = drained.awaitNanos(remaining);
                } catch (InterruptedException ignored) {
                    interrupted = true;
                    remaining -= System.nanoTime() - started;
                }
            }
            if (inFlight.get() > 0) {
                throw new IllegalStateException("Timed out draining " + inFlight.get()
                        + " feature task(s) after " + timeout.toSeconds() + " seconds");
            }
        } finally {
            drainLock.unlock();
            if (interrupted) Thread.currentThread().interrupt();
        }
    }

    private void requireOpen() {
        FeatureResourceState current = state.get();
        if (current != FeatureResourceState.OPEN) throw new IllegalStateException("Task tracker is " + current);
    }

    private static Throwable appendFailure(Throwable current, Throwable additional) {
        if (current == null) return additional;
        current.addSuppressed(additional);
        return current;
    }

    @SuppressWarnings("unchecked")
    private static <E extends Throwable> void throwUnchecked(Throwable failure) throws E { throw (E) failure; }
}
