package nl.hauntedmc.featureframework.velocity.lifecycle;

import com.velocitypowered.api.scheduler.ScheduledTask;
import com.velocitypowered.api.scheduler.Scheduler;
import nl.hauntedmc.featureframework.lifecycle.FeatureResourceState;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

/** Tracks, quiesces, cancels, and drains feature-scoped Velocity tasks. */
public class FeatureTaskManager {

    private static final Duration DRAIN_TIMEOUT = Duration.ofSeconds(30);

    private final Scheduler scheduler;
    private final Object plugin;
    private final List<ScheduledTask> scheduledTasks = new CopyOnWriteArrayList<>();
    private final AtomicReference<FeatureResourceState> state =
            new AtomicReference<>(FeatureResourceState.OPEN);
    private final AtomicInteger inFlight = new AtomicInteger();
    private final ReentrantLock drainLock = new ReentrantLock();
    private final Condition drained = drainLock.newCondition();

    public FeatureTaskManager(Scheduler scheduler, Object plugin) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    public ScheduledTask scheduleTask(Runnable task) {
        Objects.requireNonNull(task, "task");
        requireOpen();
        return scheduleOnce(r -> scheduler.buildTask(plugin, r).schedule(), task);
    }

    public ScheduledTask scheduleDelayedTask(Runnable task, Duration delay) {
        Objects.requireNonNull(task, "task");
        requireOpen();
        Duration clamped = clampDelay(delay);
        return scheduleOnce(r -> scheduler.buildTask(plugin, r).delay(clamped).schedule(), task);
    }

    public ScheduledTask scheduleRepeatingTask(Runnable task, Duration period) {
        Objects.requireNonNull(task, "task");
        requireOpen();
        Duration clamped = clampPeriod(period);
        return scheduleRepeating(r -> scheduler.buildTask(plugin, r)
                .delay(Duration.ZERO)
                .repeat(clamped)
                .schedule(), task);
    }

    public ScheduledTask scheduleRepeatingTask(Runnable task, Duration delay, Duration period) {
        Objects.requireNonNull(task, "task");
        requireOpen();
        Duration clampedDelay = clampDelay(delay);
        Duration clampedPeriod = clampPeriod(period);
        return scheduleRepeating(r -> scheduler.buildTask(plugin, r)
                .delay(clampedDelay)
                .repeat(clampedPeriod)
                .schedule(), task);
    }

    public void cancelTask(ScheduledTask task) {
        if (task == null) return;
        task.cancel();
        scheduledTasks.remove(task);
    }

    public void quiesce() {
        state.compareAndSet(FeatureResourceState.OPEN, FeatureResourceState.QUIESCING);
    }

    /** Cancels all handles, aggregates cancellation failures, and waits for running callbacks. */
    public void cancelAllTasks() {
        quiesce();
        Throwable failure = null;
        for (ScheduledTask task : new ArrayList<>(scheduledTasks)) {
            try {
                task.cancel();
                scheduledTasks.remove(task);
            } catch (Throwable cancellationFailure) {
                failure = appendFailure(failure, cancellationFailure);
            }
        }
        try {
            awaitDrain(DRAIN_TIMEOUT);
        } catch (Throwable drainFailure) {
            failure = appendFailure(failure, drainFailure);
        } finally {
            if (failure == null && scheduledTasks.isEmpty()) {
                state.set(FeatureResourceState.CLOSED);
            }
        }
        throwIfPresent(failure);
    }

    public int getActiveTaskCount() {
        return scheduledTasks.size();
    }

    public int getInFlightTaskCount() {
        return inFlight.get();
    }

    public FeatureResourceState state() {
        return state.get();
    }

    private ScheduledTask scheduleOnce(Function<Runnable, ScheduledTask> submitter, Runnable task) {
        AtomicReference<ScheduledTask> reference = new AtomicReference<>();
        AtomicBoolean completed = new AtomicBoolean();
        Runnable wrapped = () -> {
            try {
                runTracked(task);
            } finally {
                completed.set(true);
                ScheduledTask scheduled = reference.get();
                if (scheduled != null) scheduledTasks.remove(scheduled);
            }
        };
        requireOpen();
        ScheduledTask scheduled = submitter.apply(wrapped);
        if (state.get() != FeatureResourceState.OPEN) {
            scheduled.cancel();
            throw new IllegalStateException("Task manager began quiescing while scheduling a task");
        }
        reference.set(scheduled);
        scheduledTasks.add(scheduled);
        if (completed.get()) scheduledTasks.remove(scheduled);
        return scheduled;
    }

    private ScheduledTask scheduleRepeating(Function<Runnable, ScheduledTask> submitter, Runnable task) {
        requireOpen();
        ScheduledTask scheduled = submitter.apply(() -> runTracked(task));
        if (state.get() != FeatureResourceState.OPEN) {
            scheduled.cancel();
            throw new IllegalStateException("Task manager began quiescing while scheduling a task");
        }
        scheduledTasks.add(scheduled);
        return scheduled;
    }

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
        if (current != FeatureResourceState.OPEN) {
            throw new IllegalStateException("Task manager is " + current);
        }
    }

    private static Duration clampDelay(Duration duration) {
        if (duration == null || duration.isNegative()) return Duration.ZERO;
        return duration;
    }

    private static Duration clampPeriod(Duration period) {
        if (period == null || period.isZero() || period.isNegative()) return Duration.ofSeconds(1);
        return period;
    }

    private static Throwable appendFailure(Throwable current, Throwable additional) {
        if (current == null) return additional;
        current.addSuppressed(additional);
        return current;
    }

    private static void throwIfPresent(Throwable failure) {
        if (failure != null) throwUnchecked(failure);
    }

    @SuppressWarnings("unchecked")
    private static <E extends Throwable> void throwUnchecked(Throwable throwable) throws E {
        throw (E) throwable;
    }
}
