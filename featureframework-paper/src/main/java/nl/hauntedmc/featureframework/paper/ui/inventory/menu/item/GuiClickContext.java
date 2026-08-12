package nl.hauntedmc.featureframework.paper.ui.inventory.menu.item;

import nl.hauntedmc.featureframework.paper.ui.inventory.menu.GuiMenu;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;

/**
 * Context for a GUI click, passed into GuiItem actions.
 */
public final class GuiClickContext {
    private final GuiMenu menu;
    private final int slot;
    private final InventoryClickEvent event;

    public GuiClickContext(GuiMenu menu, int slot, InventoryClickEvent event) {
        this.menu = menu;
        this.slot = slot;
        this.event = event;
    }

    public GuiMenu menu() {
        return menu;
    }

    public int slot() {
        return slot;
    }

    public InventoryClickEvent rawEvent() {
        return event;
    }

    public Player player() {
        return (Player) event.getWhoClicked();
    }

    public boolean isLeftClick() {
        return event.getClick() == ClickType.LEFT || event.getClick() == ClickType.SHIFT_LEFT;
    }

    public boolean isRightClick() {
        return event.getClick() == ClickType.RIGHT || event.getClick() == ClickType.SHIFT_RIGHT;
    }

    public boolean isShiftClick() {
        return event.getClick() == ClickType.SHIFT_LEFT || event.getClick() == ClickType.SHIFT_RIGHT;
    }

    public boolean isNumberKey() {
        return event.getClick() == ClickType.NUMBER_KEY;
    }

    public boolean isMiddleClick() {
        return event.getClick() == ClickType.MIDDLE;
    }

    /**
     * Navigation helpers routed via the menu's per-feature GUI manager.
     */
    public void openChild(GuiMenu child) {
        menu.guiManager().openChild(player(), child);
    }

    public void openRoot(GuiMenu root) {
        menu.guiManager().openRoot(player(), root);
    }

    public void goBack() {
        menu.guiManager().goBack(player());
    }

    public void reopenSame() {
        menu.guiManager().reopenSame(player(), menu);
    }
}
