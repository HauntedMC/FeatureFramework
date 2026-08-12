package nl.hauntedmc.featureframework.paper.lifecycle;

import nl.hauntedmc.featureframework.lifecycle.FeatureResourceState;
import nl.hauntedmc.featureframework.paper.time.BukkitTime;
import nl.hauntedmc.featureframework.spi.lifecycle.TaskLifecycleCore;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/** Paper scheduler adapter backed by the shared feature task ownership state machine. */
public class FeatureTaskManager {
    private final Plugin plugin;
    private final TaskLifecycleCore<BukkitTask> lifecycle = new TaskLifecycleCore<>();
    private final Map<BukkitTask, CompletableFuture<?>> taskFutures = new ConcurrentHashMap<>();

    public FeatureTaskManager(Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    public BukkitTask scheduleOneTimeTask(Runnable task) {
        Objects.requireNonNull(task, "task");
        return lifecycle.scheduleOnce(
                runnable -> Bukkit.getScheduler().runTask(plugin, runnable), task, BukkitTask::cancel);
    }

    public BukkitTask scheduleDelayedTask(Runnable task, BukkitTime delay) {
        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(delay, "delay");
        long ticks = clampDelay(delay);
        return lifecycle.scheduleOnce(
                runnable -> Bukkit.getScheduler().runTaskLater(plugin, runnable, ticks), task, BukkitTask::cancel);
    }

    public BukkitTask scheduleRepeatingTask(Runnable task, BukkitTime period) {
        return scheduleRepeatingTask(task, BukkitTime.ticks(0L), period);
    }

    public BukkitTask scheduleRepeatingTask(Runnable task, BukkitTime delay, BukkitTime period) {
        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(delay, "delay");
        Objects.requireNonNull(period, "period");
        long delayTicks = clampDelay(delay);
        long periodTicks = clampPeriod(period);
        return lifecycle.scheduleRepeating(
                runnable -> Bukkit.getScheduler().runTaskTimer(plugin, runnable, delayTicks, periodTicks),
                task, BukkitTask::cancel);
    }

    public BukkitTask scheduleAsyncTask(Runnable task) {
        Objects.requireNonNull(task, "task");
        return lifecycle.scheduleOnce(
                runnable -> Bukkit.getScheduler().runTaskAsynchronously(plugin, runnable), task, BukkitTask::cancel);
    }

    public CompletableFuture<Void> runAsync(Runnable task) {
        Objects.requireNonNull(task, "task");
        return supplyAsync(() -> {
            task.run();
            return null;
        });
    }

    public <T> CompletableFuture<T> supplyAsync(Supplier<T> supplier) {
        Objects.requireNonNull(supplier, "supplier");
        CompletableFuture<T> result = new CompletableFuture<>();
        try {
            BukkitTask task = scheduleAsyncTask(() -> {
                if (result.isCancelled()) return;
                try {
                    result.complete(supplier.get());
                } catch (Throwable failure) {
                    result.completeExceptionally(failure);
                }
            });
            taskFutures.put(task, result);
            result.whenComplete((ignored, failure) -> taskFutures.remove(task, result));
        } catch (RuntimeException failure) {
            result.completeExceptionally(failure);
        }
        return result;
    }

    public BukkitTask scheduleAsyncDelayedTask(Runnable task, BukkitTime delay) {
        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(delay, "delay");
        long ticks = clampDelay(delay);
        return lifecycle.scheduleOnce(
                runnable -> Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, runnable, ticks),
                task, BukkitTask::cancel);
    }

    public BukkitTask scheduleAsyncRepeatingTask(Runnable task, BukkitTime delay, BukkitTime period) {
        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(delay, "delay");
        Objects.requireNonNull(period, "period");
        long delayTicks = clampDelay(delay);
        long periodTicks = clampPeriod(period);
        return lifecycle.scheduleRepeating(
                runnable -> Bukkit.getScheduler().runTaskTimerAsynchronously(
                        plugin, runnable, delayTicks, periodTicks),
                task, BukkitTask::cancel);
    }

    public void cancelTask(BukkitTask task) {
        lifecycle.cancel(task, this::cancelNative);
    }

    public boolean isTaskQueued(int taskId) { return Bukkit.getScheduler().isQueued(taskId); }
    public boolean isTaskRunning(int taskId) { return Bukkit.getScheduler().isCurrentlyRunning(taskId); }
    public void cancelAllTasks() { lifecycle.cancelAll(this::cancelNative); taskFutures.clear(); }
    public void quiesce() { lifecycle.quiesce(); }
    public FeatureResourceState state() { return lifecycle.state(); }
    public int getInFlightTaskCount() { return lifecycle.inFlightCount(); }
    public int getActiveTaskCount() { return lifecycle.activeCount(); }

    private void cancelNative(BukkitTask task) {
        CompletableFuture<?> future = taskFutures.remove(task);
        if (future != null) future.cancel(false);
        task.cancel();
    }

    static long clampDelay(BukkitTime time) { return Math.max(0L, time.toTicks()); }
    static long clampPeriod(BukkitTime time) { return Math.max(1L, time.toTicks()); }
}
