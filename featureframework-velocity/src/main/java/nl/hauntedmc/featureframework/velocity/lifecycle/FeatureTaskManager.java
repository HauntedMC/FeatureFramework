package nl.hauntedmc.featureframework.velocity.lifecycle;

import com.velocitypowered.api.scheduler.ScheduledTask;
import com.velocitypowered.api.scheduler.Scheduler;
import nl.hauntedmc.featureframework.lifecycle.FeatureResourceState;
import nl.hauntedmc.featureframework.lifecycle.FeatureTaskTracker;

import java.time.Duration;
import java.util.Objects;

/** Velocity scheduler adapter backed by the shared feature task ownership state machine. */
public class FeatureTaskManager {
    private final Scheduler scheduler;
    private final Object plugin;
    private final FeatureTaskTracker<ScheduledTask> tracker = new FeatureTaskTracker<>();

    public FeatureTaskManager(Scheduler scheduler, Object plugin) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    public ScheduledTask scheduleTask(Runnable task) {
        Objects.requireNonNull(task, "task");
        return tracker.scheduleOnce(
                runnable -> scheduler.buildTask(plugin, runnable).schedule(), task, ScheduledTask::cancel);
    }

    public ScheduledTask scheduleDelayedTask(Runnable task, Duration delay) {
        Objects.requireNonNull(task, "task");
        Duration clamped = clampDelay(delay);
        return tracker.scheduleOnce(
                runnable -> scheduler.buildTask(plugin, runnable).delay(clamped).schedule(),
                task, ScheduledTask::cancel);
    }

    public ScheduledTask scheduleRepeatingTask(Runnable task, Duration period) {
        return scheduleRepeatingTask(task, Duration.ZERO, period);
    }

    public ScheduledTask scheduleRepeatingTask(Runnable task, Duration delay, Duration period) {
        Objects.requireNonNull(task, "task");
        Duration clampedDelay = clampDelay(delay);
        Duration clampedPeriod = clampPeriod(period);
        return tracker.scheduleRepeating(
                runnable -> scheduler.buildTask(plugin, runnable)
                        .delay(clampedDelay)
                        .repeat(clampedPeriod)
                        .schedule(),
                task, ScheduledTask::cancel);
    }

    public void cancelTask(ScheduledTask task) { tracker.cancel(task, ScheduledTask::cancel); }
    public void quiesce() { tracker.quiesce(); }
    public void cancelAllTasks() { tracker.cancelAll(ScheduledTask::cancel); }
    public int getActiveTaskCount() { return tracker.activeCount(); }
    public int getInFlightTaskCount() { return tracker.inFlightCount(); }
    public FeatureResourceState state() { return tracker.state(); }

    private static Duration clampDelay(Duration duration) {
        if (duration == null || duration.isNegative()) return Duration.ZERO;
        return duration;
    }

    private static Duration clampPeriod(Duration period) {
        if (period == null || period.isZero() || period.isNegative()) return Duration.ofSeconds(1);
        return period;
    }
}
