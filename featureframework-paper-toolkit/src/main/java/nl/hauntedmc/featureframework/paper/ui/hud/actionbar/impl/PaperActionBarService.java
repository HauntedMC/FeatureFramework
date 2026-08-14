package nl.hauntedmc.featureframework.paper.ui.hud.actionbar.impl;

import net.kyori.adventure.text.Component;
import nl.hauntedmc.featureframework.paper.ui.hud.actionbar.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * Paper/Spigot implementation. All state changes happen on the main thread.
 */
public final class PaperActionBarService implements ActionBarService {

    private final Plugin plugin;

    // Cycle state
    private ActionBarCycle currentCycle = null;
    private int cycleIndex = 0;
    private boolean cyclePaused = false;

    // Generation protects handles from cancelling newer cycles
    private final AtomicInteger cycleGen = new AtomicInteger(0);
    private int runningGen = 0;

    // Scheduled task ids
    private int repeatingTaskId = -1;
    private int endOfEntryTaskId = -1;
    private int resumeCycleTaskId = -1;

    // Broadcast state
    private int broadcastRepeatingTaskId = -1;
    private int endBroadcastTaskId = -1;

    // Per-player timed overrides. A PAUSE_CYCLE override suppresses cycle frames only for that player.
    private final AtomicInteger targetedGeneration = new AtomicInteger();
    private final Map<UUID, TargetedOverride> targetedOverrides = new HashMap<>();

    public PaperActionBarService(@NotNull Plugin plugin) {
        this.plugin = plugin;
    }

    /* ========================= ActionBarService ========================= */

    @Override
    public @NotNull ActionBarCycleHandle startCycle(@NotNull ActionBarCycle cycle) {
        runSync(() -> {
            cancelCycleTasks();
            this.currentCycle = cycle;
            this.cycleIndex = 0;
            this.cyclePaused = false;
            this.runningGen = cycleGen.incrementAndGet();
            if (!cycle.entries().isEmpty()) {
                playCurrentEntryThenScheduleNext();
            }
        });

        final int myGen = cycleGen.get();
        return new ActionBarCycleHandle() {
            @Override
            public boolean isActive() {
                return isCycleRunning() && runningGen == myGen;
            }

            @Override
            public void cancel() {
                runSync(() -> {
                    if (runningGen == myGen) {
                        stopCycle();
                    }
                });
            }
        };
    }

    @Override
    public boolean isCycleRunning() {
        return currentCycle != null && !currentCycle.entries().isEmpty();
    }

    @Override
    public void stopCycle() {
        runSync(() -> {
            cancelCycleTasks();
            currentCycle = null;
            cycleIndex = 0;
            cyclePaused = false;
        });
    }

    @Override
    public void sendOnce(@NotNull Player player, @NotNull Component component) {
        runSync(() -> {
            clearTargetedOverride(player.getUniqueId());
            if (player.isOnline()) {
                player.sendActionBar(component);
            }
        });
    }

    @Override
    public void send(@NotNull Player player,
                     @NotNull Component component,
                     int seconds,
                     @NotNull PauseMode pauseMode) {
        runSync(() -> startTargetedOverride(player, component, seconds, pauseMode, null));
    }

    @Override
    public void sendOverride(@NotNull Player player,
                             @NotNull Component component,
                             int seconds,
                             @NotNull PauseMode pauseMode,
                             @NotNull String owner) {
        Objects.requireNonNull(owner, "owner");
        if (owner.isBlank()) {
            throw new IllegalArgumentException("Action bar override owner cannot be blank");
        }
        runSync(() -> startTargetedOverride(player, component, seconds, pauseMode, owner));
    }

    @Override
    public void clearOverride(@NotNull Player player, @NotNull String owner) {
        Objects.requireNonNull(owner, "owner");
        if (owner.isBlank()) {
            throw new IllegalArgumentException("Action bar override owner cannot be blank");
        }
        runSync(() -> clearOwnedTargetedOverride(player, owner));
    }

    @Override
    public void sendOnceBroadcast(@NotNull Component component) {
        runSync(() -> sendActionBarAll(component));
    }

    @Override
    public void sendBroadcast(@NotNull Component component, int seconds, @NotNull PauseMode mode) {
        runSync(() -> {
            if (seconds <= 0) {
                sendActionBarAll(component);
                return;
            }
            if (mode == PauseMode.PAUSE_CYCLE && isCycleRunning() && !cyclePaused) {
                pauseCycle();
            }
            startBroadcastRepeating(() -> sendActionBarAll(component), seconds);
        });
    }

    @Override
    public void sendOnceBroadcastPerPlayer(@NotNull Function<Player, Component> supplier) {
        runSync(() -> sendActionBarAllPerPlayer(supplier, false));
    }

    @Override
    public void sendBroadcastPerPlayer(@NotNull Function<Player, Component> supplier,
                                       int seconds,
                                       @NotNull PauseMode mode) {
        runSync(() -> {
            if (seconds <= 0) {
                sendActionBarAllPerPlayer(supplier, false);
                return;
            }
            if (mode == PauseMode.PAUSE_CYCLE && isCycleRunning() && !cyclePaused) {
                pauseCycle();
            }
            startBroadcastRepeating(() -> sendActionBarAllPerPlayer(supplier, false), seconds);
        });
    }

    /* ============================== Internals ============================== */

    private void playCurrentEntryThenScheduleNext() {
        List<ActionBarEntry> entries = currentCycle.entries();
        if (entries.isEmpty()) {
            return;
        }

        ActionBarEntry entry = entries.get(Math.floorMod(cycleIndex, entries.size()));

        cancelTask(repeatingTaskId);
        repeatingTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            if (entry.isPerPlayer()) {
                sendActionBarAllPerPlayer(entry.perPlayer(), true);
            } else {
                sendCycleActionBarAll(entry.component());
            }
        }, 0L, 20L);

        cancelTask(endOfEntryTaskId);
        int durationTicks = Math.max(0, entry.seconds()) * 20;
        endOfEntryTaskId = Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> {
            cancelTask(repeatingTaskId);
            repeatingTaskId = -1;

            int gapSeconds = currentCycle != null ? currentCycle.gapSeconds() : 0;
            int gapTicks = Math.max(0, gapSeconds) * 20;

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                cycleIndex++;
                if (currentCycle != null && !cyclePaused) {
                    playCurrentEntryThenScheduleNext();
                }
            }, gapTicks);
        }, durationTicks);
    }

    private void pauseCycle() {
        cyclePaused = true;
        cancelTask(repeatingTaskId);
        cancelTask(endOfEntryTaskId);
        repeatingTaskId = -1;
        endOfEntryTaskId = -1;
    }

    private void resumeCycle() {
        if (currentCycle == null || currentCycle.entries().isEmpty() || !cyclePaused) {
            return;
        }
        cyclePaused = false;
        playCurrentEntryThenScheduleNext();
    }

    private void startBroadcastRepeating(Runnable broadcastTick, int seconds) {
        cancelTask(broadcastRepeatingTaskId);
        cancelTask(endBroadcastTaskId);

        broadcastRepeatingTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, broadcastTick, 0L, 20L);
        endBroadcastTaskId = Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> {
            cancelTask(broadcastRepeatingTaskId);
            broadcastRepeatingTaskId = -1;
            if (cyclePaused) {
                cancelTask(resumeCycleTaskId);
                resumeCycleTaskId = Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, this::resumeCycle, 1L);
            }
        }, Math.max(0, seconds) * 20L);
    }

    private void startTargetedOverride(Player player,
                                       Component component,
                                       int seconds,
                                       PauseMode pauseMode,
                                       String owner) {
        UUID playerId = player.getUniqueId();
        clearTargetedOverride(playerId);
        if (seconds <= 0) {
            if (player.isOnline()) {
                player.sendActionBar(component);
            }
            return;
        }

        int generation = targetedGeneration.incrementAndGet();
        int repeating = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            TargetedOverride current = targetedOverrides.get(playerId);
            if (current == null || current.generation() != generation) {
                return;
            }
            Player online = Bukkit.getPlayer(playerId);
            if (online == null || !online.isOnline()) {
                clearTargetedOverride(playerId);
                return;
            }
            online.sendActionBar(component);
        }, 0L, 20L);

        int ending = Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> {
            TargetedOverride current = targetedOverrides.get(playerId);
            if (current != null && current.generation() == generation) {
                clearTargetedOverride(playerId);
            }
        }, Math.max(0, seconds) * 20L);

        targetedOverrides.put(playerId, new TargetedOverride(
                generation,
                repeating,
                ending,
                pauseMode == PauseMode.PAUSE_CYCLE,
                owner
        ));
    }

    private void clearOwnedTargetedOverride(Player player, String owner) {
        UUID playerId = player.getUniqueId();
        TargetedOverride current = targetedOverrides.get(playerId);
        if (current == null || !owner.equals(current.owner())) {
            return;
        }
        clearTargetedOverride(playerId);
        if (player.isOnline()) {
            player.sendActionBar(Component.empty());
        }
    }

    private void clearTargetedOverride(UUID playerId) {
        TargetedOverride previous = targetedOverrides.remove(playerId);
        if (previous == null) {
            return;
        }
        cancelTask(previous.repeatingTaskId());
        cancelTask(previous.endTaskId());
    }

    private boolean cycleSuppressedFor(Player player) {
        TargetedOverride override = targetedOverrides.get(player.getUniqueId());
        return override != null && override.pauseCycle();
    }

    private void cancelCycleTasks() {
        cancelTask(repeatingTaskId);
        cancelTask(endOfEntryTaskId);
        cancelTask(resumeCycleTaskId);
        repeatingTaskId = -1;
        endOfEntryTaskId = -1;
        resumeCycleTaskId = -1;
    }

    private void cancelTask(int taskId) {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
        }
    }

    private void sendCycleActionBarAll(Component component) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!cycleSuppressedFor(player)) {
                player.sendActionBar(component);
            }
        }
    }

    private void sendActionBarAll(Component component) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendActionBar(component);
        }
    }

    private void sendActionBarAllPerPlayer(@NotNull Function<Player, Component> supplier,
                                           boolean cycleFrame) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (cycleFrame && cycleSuppressedFor(player)) {
                continue;
            }
            Component component = supplier.apply(player);
            if (component != null) {
                player.sendActionBar(component);
            }
        }
    }

    private void runSync(Runnable runnable) {
        if (Bukkit.isPrimaryThread()) {
            runnable.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, runnable);
        }
    }

    /**
     * Call this on plugin disable before unpublishing the service from {@code ActionBars}.
     */
    public void shutdown() {
        runSync(() -> {
            cancelCycleTasks();
            cancelTask(broadcastRepeatingTaskId);
            cancelTask(endBroadcastTaskId);
            broadcastRepeatingTaskId = -1;
            endBroadcastTaskId = -1;
            for (UUID playerId : List.copyOf(targetedOverrides.keySet())) {
                clearTargetedOverride(playerId);
            }
            currentCycle = null;
            cycleIndex = 0;
            cyclePaused = false;
        });
    }

    private record TargetedOverride(int generation,
                                    int repeatingTaskId,
                                    int endTaskId,
                                    boolean pauseCycle,
                                    String owner) {
    }
}
