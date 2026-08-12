package nl.hauntedmc.featureframework.paper.lifecycle;

import nl.hauntedmc.featureframework.paper.time.BukkitTime;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class FeatureTaskManagerTest {

    @Test
    void clampDelayNeverReturnsNegative() {
        assertEquals(0L, FeatureTaskManager.clampDelay(BukkitTime.ticks(-5)));
        assertEquals(3L, FeatureTaskManager.clampDelay(BukkitTime.ticks(3)));
    }

    @Test
    void clampPeriodHasMinimumOneTick() {
        assertEquals(1L, FeatureTaskManager.clampPeriod(BukkitTime.ticks(0)));
        assertEquals(1L, FeatureTaskManager.clampPeriod(BukkitTime.ticks(-2)));
        assertEquals(4L, FeatureTaskManager.clampPeriod(BukkitTime.ticks(4)));
    }

    @Test
    void oneShotTaskCompletingBeforeSchedulerReturnsIsNotLeaked() {
        Plugin plugin = mock(Plugin.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        BukkitTask task = mock(BukkitTask.class);
        when(scheduler.runTask(eq(plugin), any(Runnable.class))).thenAnswer(invocation -> {
            invocation.<Runnable>getArgument(1).run();
            return task;
        });

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            FeatureTaskManager manager = new FeatureTaskManager(plugin);
            AtomicInteger executions = new AtomicInteger();

            manager.scheduleOneTimeTask(executions::incrementAndGet);

            assertEquals(1, executions.get());
            assertEquals(0, manager.getActiveTaskCount());
            verify(task, never()).cancel();
        }
    }

    @Test
    void delayedTaskIsTrackedUntilItsWrapperCompletes() {
        Plugin plugin = mock(Plugin.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        BukkitTask task = mock(BukkitTask.class);
        ArgumentCaptor<Runnable> runnable = ArgumentCaptor.forClass(Runnable.class);
        when(scheduler.runTaskLater(eq(plugin), runnable.capture(), eq(40L))).thenReturn(task);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            FeatureTaskManager manager = new FeatureTaskManager(plugin);
            AtomicInteger executions = new AtomicInteger();

            manager.scheduleDelayedTask(executions::incrementAndGet, BukkitTime.ticks(40));
            assertEquals(1, manager.getActiveTaskCount());

            runnable.getValue().run();

            assertEquals(1, executions.get());
            assertEquals(0, manager.getActiveTaskCount());
        }
    }

    @Test
    void cancellingTrackedTaskCancelsHandleAndRemovesIt() {
        Plugin plugin = mock(Plugin.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        BukkitTask task = mock(BukkitTask.class);
        when(scheduler.runTaskLater(eq(plugin), any(Runnable.class), eq(1L))).thenReturn(task);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            FeatureTaskManager manager = new FeatureTaskManager(plugin);

            BukkitTask returned = manager.scheduleDelayedTask(() -> { }, BukkitTime.ticks(1));
            manager.cancelTask(returned);

            verify(task).cancel();
            assertEquals(0, manager.getActiveTaskCount());
        }
    }

    @Test
    void cancellingUnknownOrNullTaskIsSafe() {
        Plugin plugin = mock(Plugin.class);
        BukkitTask task = mock(BukkitTask.class);
        FeatureTaskManager manager = new FeatureTaskManager(plugin);

        manager.cancelTask(null);
        manager.cancelTask(task);

        verify(task).cancel();
        assertEquals(0, manager.getActiveTaskCount());
    }

    @Test
    void supplyAsyncCompletesWithValueAndRemovesFinishedTask() {
        Plugin plugin = mock(Plugin.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        BukkitTask task = mock(BukkitTask.class);
        ArgumentCaptor<Runnable> runnable = ArgumentCaptor.forClass(Runnable.class);
        when(scheduler.runTaskAsynchronously(eq(plugin), runnable.capture())).thenReturn(task);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            FeatureTaskManager manager = new FeatureTaskManager(plugin);

            CompletableFuture<String> future = manager.supplyAsync(() -> "done");
            assertFalse(future.isDone());
            assertEquals(1, manager.getActiveTaskCount());

            runnable.getValue().run();

            assertEquals("done", future.join());
            assertEquals(0, manager.getActiveTaskCount());
        }
    }

    @Test
    void supplyAsyncPropagatesSupplierFailure() {
        Plugin plugin = mock(Plugin.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        BukkitTask task = mock(BukkitTask.class);
        ArgumentCaptor<Runnable> runnable = ArgumentCaptor.forClass(Runnable.class);
        when(scheduler.runTaskAsynchronously(eq(plugin), runnable.capture())).thenReturn(task);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            FeatureTaskManager manager = new FeatureTaskManager(plugin);

            CompletableFuture<String> future = manager.supplyAsync(() -> {
                throw new IllegalStateException("boom");
            });
            runnable.getValue().run();

            CompletionException exception = assertThrows(CompletionException.class, future::join);
            assertTrue(exception.getCause() instanceof IllegalStateException);
            assertEquals("boom", exception.getCause().getMessage());
            assertEquals(0, manager.getActiveTaskCount());
        }
    }

    @Test
    void schedulerRejectionCompletesAsyncFutureExceptionally() {
        Plugin plugin = mock(Plugin.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        when(scheduler.runTaskAsynchronously(eq(plugin), any(Runnable.class)))
                .thenThrow(new IllegalStateException("scheduler stopped"));

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            FeatureTaskManager manager = new FeatureTaskManager(plugin);

            CompletableFuture<String> future = manager.supplyAsync(() -> "unused");

            CompletionException exception = assertThrows(CompletionException.class, future::join);
            assertEquals("scheduler stopped", exception.getCause().getMessage());
            assertEquals(0, manager.getActiveTaskCount());
        }
    }

    @Test
    void cancelAllTasksCancelsHandlesAndAssociatedFutures() {
        Plugin plugin = mock(Plugin.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        BukkitTask first = mock(BukkitTask.class);
        BukkitTask second = mock(BukkitTask.class);
        when(scheduler.runTaskAsynchronously(eq(plugin), any(Runnable.class))).thenReturn(first, second);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            FeatureTaskManager manager = new FeatureTaskManager(plugin);
            CompletableFuture<String> firstFuture = manager.supplyAsync(() -> "first");
            CompletableFuture<String> secondFuture = manager.supplyAsync(() -> "second");

            manager.cancelAllTasks();

            assertTrue(firstFuture.isCancelled());
            assertTrue(secondFuture.isCancelled());
            verify(first).cancel();
            verify(second).cancel();
            assertEquals(0, manager.getActiveTaskCount());
        }
    }
}
