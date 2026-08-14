package nl.hauntedmc.featureframework.paper.ui.inventory.menu;

import net.kyori.adventure.text.Component;
import nl.hauntedmc.featureframework.paper.lifecycle.FeatureTaskManager;
import nl.hauntedmc.featureframework.paper.time.BukkitTime;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Feature-scoped GUI manager.
 * - Listener is registered by FeatureLifecycleManager.
 * - Schedules work via FeatureTaskManager (main-thread).
 */
public final class FeatureGUIManager implements Listener, MenuRuntime {

    private final Plugin plugin;
    private final FeatureTaskManager taskManager;

    private final Map<UUID, Deque<GuiMenu>> backStacks = new ConcurrentHashMap<>();
    private final Map<UUID, GuiMenu> openMenus = new ConcurrentHashMap<>();
    private final Map<UUID, Long> clickDebounce = new ConcurrentHashMap<>();

    private static final long CLICK_DEBOUNCE_MS = 120L;

    public FeatureGUIManager(Plugin plugin, FeatureTaskManager taskManager) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.taskManager = Objects.requireNonNull(taskManager, "taskManager");
    }

    /**
     * Clear all in-memory state. Intended for feature unload.
     */
    public void shutdown() {
        try {
            // Best effort: close any still-open inventories
            for (UUID uuid : openMenus.keySet()) {
                Player p = plugin.getServer().getPlayer(uuid);
                if (p != null && p.isOnline()) {
                    p.closeInventory(InventoryCloseEvent.Reason.PLUGIN);
                }
            }
        } finally {
            openMenus.clear();
            backStacks.clear();
            clickDebounce.clear();
        }
    }

    /**
     * Open a new root menu, clearing the back stack.
     */
    public void openRoot(Player p, GuiMenu menu) {
        if (!isUsable(p)) return;
        backStacks.computeIfAbsent(p.getUniqueId(), k -> new ArrayDeque<>()).clear();
        openInternal(p, menu, false, OpenSemantics.REPLACE);
    }

    /**
     * Open a child menu, pushing the current menu.
     */
    public void openChild(Player p, GuiMenu menu) {
        if (!isUsable(p)) return;
        GuiMenu current = openMenus.get(p.getUniqueId());
        if (current != null) {
            backStacks.computeIfAbsent(p.getUniqueId(), k -> new ArrayDeque<>()).push(current);
        }
        openInternal(p, menu, true, OpenSemantics.REPLACE);
    }

    /**
     * Reopen same instance (pagination/refresh).
     */
    public void reopenSame(Player p, GuiMenu sameMenu) {
        if (!isUsable(p)) return;
        GuiMenu current = openMenus.get(p.getUniqueId());
        if (current != sameMenu) return;
        openInternal(p, sameMenu, false, OpenSemantics.REOPEN);
    }

    /**
     * Navigate back if possible.
     */
    public boolean goBack(Player p) {
        Deque<GuiMenu> stack = backStacks.get(p.getUniqueId());
        if (stack == null || stack.isEmpty()) return false;
        GuiMenu previous = stack.pop();
        openInternal(p, previous, false, OpenSemantics.REPLACE);
        return true;
    }

    @Override
    public void registerListener(Listener listener) {
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
    }

    @Override
    public void schedule(Runnable task) {
        taskManager.scheduleOneTimeTask(task);
    }

    @Override
    public void scheduleDelayed(Runnable task, BukkitTime delay) {
        taskManager.scheduleDelayedTask(task, delay);
    }

    public boolean isMenuOpen(Player p) {
        return openMenus.containsKey(p.getUniqueId());
    }

    private enum OpenSemantics {REPLACE, REOPEN}

    private boolean isUsable(Player p) {
        return p != null && p.isOnline() && !p.isDead();
    }

    private void openInternal(Player p, GuiMenu menu, boolean child, OpenSemantics sem) {
        // Ensure on main thread via feature task manager
        taskManager.scheduleOneTimeTask(() -> doOpen(p, menu, child, sem));
    }

    private void doOpen(Player p, GuiMenu menu, boolean child, OpenSemantics sem) {
        if (!isUsable(p)) return;

        menu.prepare(child);

        Component newTitle = menu.titleFor(p);
        Inventory previousTop = null;
        GuiMenu currentlyOpen = openMenus.get(p.getUniqueId());
        if (currentlyOpen == menu) {
            previousTop = p.getOpenInventory().getTopInventory();
        }

        Inventory inv = menu.createInventory(newTitle);
        menu.populate(p, inv);

        if (sem == OpenSemantics.REOPEN && previousTop != null) {
            // Defer the actual open to next tick to avoid race with current view
            taskManager.scheduleOneTimeTask(() -> {
                if (!isUsable(p)) return;
                p.openInventory(inv);
                openMenus.put(p.getUniqueId(), menu);
                menu.onOpen(p);
            });
        } else {
            p.openInventory(inv);
            openMenus.put(p.getUniqueId(), menu);
            menu.onOpen(p);
        }
    }

    /* ---------- Event handlers ---------- */

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        GuiMenu menu = openMenus.get(p.getUniqueId());
        if (menu == null) return;

        if (!menu.owns(e.getView().getTopInventory())) return;

        long now = System.currentTimeMillis();
        Long prev = clickDebounce.put(p.getUniqueId(), now);
        if (prev != null && (now - prev) < CLICK_DEBOUNCE_MS) {
            e.setCancelled(true);
            return;
        }

        e.setCancelled(true);

        if (e.getClickedInventory() == null) return;
        if (!menu.owns(e.getClickedInventory())) return;

        int slot = e.getSlot();
        try {
            menu.handleClick(p, slot, e);
        } catch (Throwable t) {
            plugin.getLogger().severe("Error handling GUI click: " + t.getMessage());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        GuiMenu menu = openMenus.get(p.getUniqueId());
        if (menu == null) return;

        Inventory top = e.getView().getTopInventory();
        if (!menu.owns(top)) return;

        for (int rawSlot : e.getRawSlots()) {
            if (rawSlot < top.getSize()) {
                e.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCreative(InventoryCreativeEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        GuiMenu menu = openMenus.get(p.getUniqueId());
        if (menu == null) return;
        if (menu.owns(e.getView().getTopInventory())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        if (isMenuOpen(p)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        Player p = e.getPlayer();
        GuiMenu menu = openMenus.get(p.getUniqueId());
        if (menu == null) return;
        e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player p)) return;
        GuiMenu menu = openMenus.get(p.getUniqueId());
        if (menu == null) return;
        if (!menu.owns(e.getInventory())) return;

        try {
            menu.onClose(p, e.getReason());
        } catch (Throwable t) {
            plugin.getLogger().severe("Error in onClose: " + t.getMessage());
        }

        boolean reopen = menu.shouldReopen() && menu.allowReopenFor(e.getReason());
        if (!reopen) {
            openMenus.remove(p.getUniqueId());
        } else {
            menu.clearReopenRequest();
            reopenSame(p, menu);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        cleanup(e.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent e) {
        if (isMenuOpen(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onKick(PlayerKickEvent e) {
        cleanup(e.getPlayer().getUniqueId());
    }

    private void cleanup(UUID uuid) {
        openMenus.remove(uuid);
        backStacks.remove(uuid);
        clickDebounce.remove(uuid);
    }
}
