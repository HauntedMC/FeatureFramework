package nl.hauntedmc.featureframework.paper.ui.inventory.menu;

import net.kyori.adventure.text.Component;
import nl.hauntedmc.featureframework.paper.ui.inventory.menu.item.GuiClickContext;
import nl.hauntedmc.featureframework.paper.ui.inventory.menu.item.GuiItem;
import nl.hauntedmc.featureframework.paper.ui.inventory.menu.item.GuiItemHelper;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Base class for inventory-based GUIs.
 */
public abstract class GuiMenu implements InventoryHolder {

    protected final MenuNavigator gui; // per-feature navigator

    protected final Component baseTitle;
    protected final int size; // multiple of 9 in [9..54]
    protected final boolean showPageInTitle;
    protected final ItemStack filler;
    protected final Map<Integer, GuiItem> fixedItems = new HashMap<>();

    protected Inventory inventory;

    // Navigation
    protected boolean addBackButton;
    protected int backSlot;

    // Reopen control
    private boolean requestReopen = false;

    // Optional sounds
    protected Sound openSound = null;
    protected float openVol = 1f, openPitch = 1f;
    protected Sound backSound = null;
    protected float backVol = 1f, backPitch = 1f;
    protected Sound closeSound = null;
    protected float closeVol = 1f, closePitch = 1f;

    protected GuiMenu(
            MenuNavigator gui,
            Component baseTitle,
            int size,
            boolean showPageInTitle,
            ItemStack filler,
            Map<Integer, GuiItem> items,
            boolean addBackButton,
            int backSlot
    ) {
        this.gui = Objects.requireNonNull(gui, "gui");
        if (size <= 0 || size % 9 != 0 || size > 54) {
            throw new IllegalArgumentException("Invalid inventory size: " + size);
        }
        this.baseTitle = Objects.requireNonNull(baseTitle, "baseTitle");
        this.size = size;
        this.showPageInTitle = showPageInTitle;
        this.filler = filler;
        if (items != null) this.fixedItems.putAll(items);
        this.addBackButton = addBackButton;
        this.backSlot = backSlot;
    }

    /**
     * Access to the per-feature GUI manager.
     */
    public MenuNavigator guiManager() {
        return gui;
    }

    /**
     * Called before rendering and before inventory creation.
     */
    public void prepare(boolean child) {
        if (child && addBackButton && backSlot < 0) backSlot = size - 1;
    }

    /**
     * Called after the menu has been opened for the player.
     */
    public void onOpen(Player p) {
        if (openSound != null) p.playSound(p.getLocation(), openSound, openVol, openPitch);
    }

    /**
     * Called when the menu closes.
     */
    public void onClose(Player p, InventoryCloseEvent.Reason reason) {
        if (closeSound != null) p.playSound(p.getLocation(), closeSound, closeVol, closePitch);
    }

    /**
     * Called when the user taps the back button.
     */
    protected void onBack(Player p) {
        if (backSound != null) p.playSound(p.getLocation(), backSound, backVol, backPitch);
    }

    /**
     * Whether the manager should reopen the same menu after a close.
     */
    public boolean shouldReopen() {
        return requestReopen;
    }

    /**
     * Request that the manager reopens the same menu next tick after close.
     */
    public void requestReopen() {
        this.requestReopen = true;
    }

    /**
     * Internal: manager clears the reopen request after honoring it.
     */
    public void clearReopenRequest() {
        this.requestReopen = false;
    }

    /**
     * Which close reasons are allowed to trigger a reopen.
     */
    public boolean allowReopenFor(InventoryCloseEvent.Reason reason) {
        return switch (reason) {
            case UNLOADED, DISCONNECT, OPEN_NEW, TELEPORT, DEATH, PLUGIN -> false;
            default -> true;
        };
    }

    /**
     * Calculate the title to use for this player.
     */
    public Component titleFor(Player p) {
        return baseTitle;
    }

    /**
     * Create a fresh inventory for this menu (Adventure title supported).
     */
    public Inventory createInventory(Component title) {
        this.inventory = Bukkit.createInventory(this, size, title);
        return this.inventory;
    }

    /**
     * Populate items into the inventory.
     */
    public void populate(Player p, Inventory inv) {
        if (filler != null) {
            for (int i = 0; i < size; i++) inv.setItem(i, filler);
        }
        placeFixedItems(p, inv);
        if (addBackButton) placeBackButton(inv);
        afterPopulate(p, inv);
    }

    /**
     * Extension point for subclasses after base population.
     */
    protected void afterPopulate(Player p, Inventory inv) {
    }

    /**
     * Place all fixed items into their slots, respecting visibility and replacements.
     */
    protected void placeFixedItems(Player p, Inventory inv) {
        for (Map.Entry<Integer, GuiItem> e : fixedItems.entrySet()) {
            int slot = e.getKey();
            if (slot < 0 || slot >= size) continue;
            GuiItem gi = e.getValue();
            if (!gi.visibleTo(p)) {
                var repl = gi.replacementOrNull(p);
                if (repl != null) inv.setItem(slot, repl);
                continue;
            }
            inv.setItem(slot, gi.renderFor(p));
        }
    }

    /**
     * Place the back button item into the configured backSlot, if valid.
     */
    protected void placeBackButton(Inventory inv) {
        if (backSlot < 0 || backSlot >= size) return;
        inv.setItem(backSlot, GuiItemHelper.backArrow());
    }

    /**
     * Handle a click inside this menu.
     */
    public void handleClick(Player p, int slot, org.bukkit.event.inventory.InventoryClickEvent e) {
        if (addBackButton && slot == backSlot) {
            onBack(p);
            gui.goBack(p);
            return;
        }
        GuiItem gi = fixedItems.get(slot);
        if (gi == null) return;
        if (!gi.visibleTo(p)) return;
        gi.click(p, new GuiClickContext(this, slot, e));
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    /**
     * Ownership check by InventoryHolder identity.
     */
    public boolean owns(Inventory inv) {
        return inv != null && inv.getHolder() == this;
    }

    /* ---------- Validation helpers for builders ---------- */

    protected static void validateFixedItems(Map<Integer, GuiItem> items, int size, boolean backEnabled, int backSlot, String who) {
        for (int s : items.keySet()) {
            if (s < 0 || s >= size)
                throw new IllegalArgumentException(who + ": fixed item slot out of bounds: " + s);
        }
        if (backEnabled && backSlot >= 0) {
            if (backSlot >= size) throw new IllegalArgumentException(who + ": backSlot out of bounds");
            if (items.containsKey(backSlot))
                throw new IllegalArgumentException(who + ": fixed items cannot override backSlot " + backSlot);
        }
    }

    protected static void ensureSlotsInBounds(Iterable<Integer> slots, int size, String label) {
        for (int s : slots) {
            if (s < 0 || s >= size) throw new IllegalArgumentException(label + " contains out-of-bounds slot: " + s);
        }
    }

    protected static void validateNoCollisionsWithBackAndFixed(
            Map<Integer, GuiItem> fixed,
            int size,
            boolean backEnabled,
            int backSlot,
            Set<Integer> reserved,
            String who
    ) {
        ensureSlotsInBounds(reserved, size, who + ".reservedSlots");
        for (int s : fixed.keySet()) {
            if (reserved.contains(s))
                throw new IllegalArgumentException(who + ": fixed item collides with reserved slot " + s);
        }
        if (backEnabled && backSlot >= 0) {
            if (reserved.contains(backSlot))
                throw new IllegalArgumentException(who + ": back button collides with reserved slot " + backSlot);
        }
    }

    /* ---------- Optional UX setters ---------- */

    public GuiMenu openSound(Sound s, float vol, float pitch) {
        this.openSound = s;
        this.openVol = vol;
        this.openPitch = pitch;
        return this;
    }

    public GuiMenu backSound(Sound s, float vol, float pitch) {
        this.backSound = s;
        this.backVol = vol;
        this.backPitch = pitch;
        return this;
    }

    public GuiMenu closeSound(Sound s, float vol, float pitch) {
        this.closeSound = s;
        this.closeVol = vol;
        this.closePitch = pitch;
        return this;
    }
}
