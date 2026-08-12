package nl.hauntedmc.featureframework.paper.lifecycle;

import nl.hauntedmc.featureframework.lifecycle.FeatureResourceState;
import nl.hauntedmc.featureframework.paper.time.BukkitTime;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Centralized scheduler for feature-scoped tasks.
 *
 * <p>Every submitted task is tracked so feature shutdown can cancel outstanding work. One-shot tasks
 * remove themselves after completion, including tasks that complete before the scheduler returns their
 * handle.</p>
 */
public class FeatureTaskManager {
    private static final Duration DRAIN_TIMEOUT = Duration.ofSeconds(30);

    private final Plugin plugin;
    private final List<BukkitTask> scheduledTasks = Collections.synchronizedList(new ArrayList<>());
    private final Map<BukkitTask, CompletableFuture<?>> taskFutures = new ConcurrentHashMap<>();
    private final Object taskRegistrationLock = new Object();
    private final AtomicReference<FeatureResourceState> state =
            new AtomicReference<>(FeatureResourceState.OPEN);
    private final AtomicInteger inFlight = new AtomicInteger();
    private final ReentrantLock drainLock = new ReentrantLock();
    private final Condition drained = drainLock.newCondition();


    public FeatureTaskManager(Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    public BukkitTask scheduleOneTimeTask(Runnable task) {
        Objects.requireNonNull(task, "task");
        return scheduleOnce(runnable -> Bukkit.getScheduler().runTask(plugin, runnable), task);
    }

    public BukkitTask scheduleDelayedTask(Runnable task, BukkitTime delay) {
        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(delay, "delay");
        long delayTicks = clampDelay(delay);
        return scheduleOnce(
                runnable -> Bukkit.getScheduler().runTaskLater(plugin, runnable, delayTicks),
                task
        );
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
        return scheduleRepeating(
                runnable -> Bukkit.getScheduler().runTaskTimer(plugin, runnable, delayTicks, periodTicks),
                task
        );
    }

    public BukkitTask scheduleAsyncTask(Runnable task) {
        Objects.requireNonNull(task, "task");
        return scheduleOnce(
                runnable -> Bukkit.getScheduler().runTaskAsynchronously(plugin, runnable),
                task
        );
    }

    /**
     * Runs feature-scoped asynchronous work and returns a future that is cancelled with the feature.
     */
    public CompletableFuture<Void> runAsync(Runnable task) {
        Objects.requireNonNull(task, "task");
        return supplyAsync(() -> {
            task.run();
            return null;
        });
    }

    /**
     * Runs a blocking or computational supplier on Bukkit's asynchronous scheduler.
     *
     * <p>This deliberately avoids both the Bukkit main thread and the JVM common pool. The returned
     * future is cancelled when its tracked Bukkit task is cancelled.</p>
     */
    public <T> CompletableFuture<T> supplyAsync(Supplier<T> supplier) {
        Objects.requireNonNull(supplier, "supplier");
        CompletableFuture<T> result = new CompletableFuture<>();

        try {
            synchronized (taskRegistrationLock) {
                BukkitTask task = scheduleAsyncTask(() -> {
                    if (result.isCancelled()) {
                        return;
                    }
                    try {
                        result.complete(supplier.get());
                    } catch (Throwable throwable) {
                        result.completeExceptionally(throwable);
                    }
                });

                taskFutures.put(task, result);
                result.whenComplete((ignored, throwable) -> taskFutures.remove(task, result));
            }
        } catch (RuntimeException exception) {
            result.completeExceptionally(exception);
        }

        return result;
    }

    public BukkitTask scheduleAsyncDelayedTask(Runnable task, BukkitTime delay) {
        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(delay, "delay");
        long delayTicks = clampDelay(delay);
        return scheduleOnce(
                runnable -> Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, runnable, delayTicks),
                task
        );
    }

    public BukkitTask scheduleAsyncRepeatingTask(Runnable task, BukkitTime delay, BukkitTime period) {
        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(delay, "delay");
        Objects.requireNonNull(period, "period");
        long delayTicks = clampDelay(delay);
        long periodTicks = clampPeriod(period);
        return scheduleRepeating(
                runnable -> Bukkit.getScheduler().runTaskTimerAsynchronously(
                        plugin,
                        runnable,
                        delayTicks,
                        periodTicks
                ),
                task
        );
    }

    public void cancelTask(BukkitTask task) {
        if (task == null) {
            return;
        }

        synchronized (taskRegistrationLock) {
            CompletableFuture<?> future = taskFutures.remove(task);
            if (future != null) {
                future.cancel(false);
            }
            task.cancel();
            scheduledTasks.remove(task);
        }
    }

    public boolean isTaskQueued(int taskId) {
        return Bukkit.getScheduler().isQueued(taskId);
    }

    public boolean isTaskRunning(int taskId) {
        return Bukkit.getScheduler().isCurrentlyRunning(taskId);
    }

    public void cancelAllTasks() {
        quiesce();
        Throwable failure = null;
        synchronized (taskRegistrationLock) {
            synchronized (scheduledTasks) {
                for (BukkitTask task : List.copyOf(scheduledTasks)) {
                    try {
                        CompletableFuture<?> future = taskFutures.remove(task);
                        if (future != null) {
                            future.cancel(false);
                        }
                        task.cancel();
                        scheduledTasks.remove(task);
                    } catch (Throwable throwable) {
                        failure = appendFailure(failure, throwable);
                    }
                }
            }
            taskFutures.clear();
        }
        try {
            awaitDrain(DRAIN_TIMEOUT);
        } catch (Throwable throwable) {
            failure = appendFailure(failure, throwable);
        }
        if (failure == null && scheduledTasks.isEmpty()) {
            state.set(FeatureResourceState.CLOSED);
        }
        if (failure != null) {
            throwUnchecked(failure);
        }
    }

    public void quiesce() {
        state.compareAndSet(FeatureResourceState.OPEN, FeatureResourceState.QUIESCING);
    }

    public FeatureResourceState state() {
        return state.get();
    }

    public int getInFlightTaskCount() {
        return inFlight.get();
    }

    public int getActiveTaskCount() {
        synchronized (scheduledTasks) {
            return scheduledTasks.size();
        }
    }

    private BukkitTask scheduleOnce(Function<Runnable, BukkitTask> submitter, Runnable task) {
        AtomicReference<BukkitTask> taskReference = new AtomicReference<>();
        AtomicBoolean completed = new AtomicBoolean(false);

        Runnable wrapped = () -> {
            try {
                runTracked(task);
            } finally {
                completed.set(true);
                BukkitTask handle = taskReference.get();
                if (handle != null) {
                    scheduledTasks.remove(handle);
                    taskFutures.remove(handle);
                }
            }
        };

        synchronized (taskRegistrationLock) {
            requireOpen();
            BukkitTask bukkitTask = submitter.apply(wrapped);
            if (state.get() != FeatureResourceState.OPEN) {
                bukkitTask.cancel();
                throw new IllegalStateException("Task manager began quiescing while scheduling a task");
            }
            taskReference.set(bukkitTask);
            scheduledTasks.add(bukkitTask);

            if (completed.get()) {
                scheduledTasks.remove(bukkitTask);
                taskFutures.remove(bukkitTask);
            }

            return bukkitTask;
        }
    }

    private BukkitTask scheduleRepeating(Function<Runnable, BukkitTask> submitter, Runnable task) {
        synchronized (taskRegistrationLock) {
            requireOpen();
            BukkitTask bukkitTask = submitter.apply(() -> runTracked(task));
            if (state.get() != FeatureResourceState.OPEN) {
                bukkitTask.cancel();
                throw new IllegalStateException("Task manager began quiescing while scheduling a task");
            }
            scheduledTasks.add(bukkitTask);
            return bukkitTask;
        }
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
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void requireOpen() {
        FeatureResourceState current = state.get();
        if (current != FeatureResourceState.OPEN) {
            throw new IllegalStateException("Task manager is " + current);
        }
    }

    private static Throwable appendFailure(Throwable current, Throwable additional) {
        if (current == null) return additional;
        current.addSuppressed(additional);
        return current;
    }

    @SuppressWarnings("unchecked")
    private static <E extends Throwable> void throwUnchecked(Throwable throwable) throws E {
        throw (E) throwable;
    }

    static long clampDelay(BukkitTime time) {
        return Math.max(0L, time.toTicks());
    }

    static long clampPeriod(BukkitTime time) {
        return Math.max(1L, time.toTicks());
    }
}
