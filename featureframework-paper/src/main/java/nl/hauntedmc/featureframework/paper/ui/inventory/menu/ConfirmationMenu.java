package nl.hauntedmc.featureframework.paper.ui.inventory.menu;

import net.kyori.adventure.text.Component;
import nl.hauntedmc.featureframework.paper.ui.inventory.menu.item.GuiItemHelper;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

/**
 * Lightweight confirmation dialog with Yes/No buttons and optional back button.
 */
public final class ConfirmationMenu extends GuiMenu {
    private final Component question;
    private final Runnable onConfirm;
    private final Runnable onCancel;
    private final int yesSlot;
    private final int noSlot;

    private ConfirmationMenu(
            MenuNavigator gui,
            Component title,
            int size,
            org.bukkit.inventory.ItemStack filler,
            Component question,
            Runnable onConfirm,
            Runnable onCancel,
            int yesSlot,
            int noSlot,
            boolean addBackButton,
            int backSlot
    ) {
        super(gui, title, size, false, filler, java.util.Map.of(), addBackButton, backSlot);
        this.question = question;
        this.onConfirm = onConfirm;
        this.onCancel = onCancel;
        this.yesSlot = yesSlot;
        this.noSlot = noSlot;
    }

    public static Builder builder(MenuNavigator gui) {
        return new Builder(gui);
    }

    @Override
    protected void afterPopulate(Player p, Inventory inv) {
        int center = (size / 9) / 2 * 9 + 4;
        inv.setItem(center, GuiItemHelper.info(question));
        inv.setItem(yesSlot, GuiItemHelper.menuItem(Material.LIME_CONCRETE, Component.text("Confirm")));
        inv.setItem(noSlot, GuiItemHelper.menuItem(Material.RED_CONCRETE, Component.text("Cancel")));
    }

    @Override
    public void handleClick(Player p, int slot, InventoryClickEvent e) {
        if (slot == yesSlot) {
            if (onConfirm != null) onConfirm.run();
            p.closeInventory();
            return;
        }
        if (slot == noSlot) {
            if (onCancel != null) onCancel.run();
            gui.goBack(p);
            return;
        }
        super.handleClick(p, slot, e);
    }

    public static final class Builder {
        private final MenuNavigator gui;
        private Component title = Component.text("Confirm");
        private Component question = Component.text("Are you sure?");
        private int size = 27;
        private org.bukkit.inventory.ItemStack filler = null;
        private boolean fillerExplicitlySet = false;
        private Runnable onConfirm, onCancel;
        private int yesSlot = 11;
        private int noSlot = 15;
        private boolean backButton = true;
        private int backSlot = 26;

        public Builder(MenuNavigator gui) {
            this.gui = java.util.Objects.requireNonNull(gui, "gui");
        }

        public Builder title(Component t) {
            this.title = t;
            return this;
        }

        public Builder question(Component q) {
            this.question = q;
            return this;
        }

        public Builder size(int s) {
            this.size = s;
            return this;
        }

        public Builder filler(org.bukkit.inventory.ItemStack i) {
            this.filler = i;
            this.fillerExplicitlySet = true;
            return this;
        }

        public Builder onConfirm(Runnable r) {
            this.onConfirm = r;
            return this;
        }

        public Builder onCancel(Runnable r) {
            this.onCancel = r;
            return this;
        }

        public Builder yesSlot(int s) {
            this.yesSlot = s;
            return this;
        }

        public Builder noSlot(int s) {
            this.noSlot = s;
            return this;
        }

        public Builder backButton(boolean b) {
            this.backButton = b;
            return this;
        }

        public Builder backButtonSlot(int s) {
            this.backSlot = s;
            return this;
        }

        public ConfirmationMenu build() {
            if (size <= 0 || size % 9 != 0 || size > 54) throw new IllegalArgumentException("Invalid size");
            if (yesSlot < 0 || yesSlot >= size || noSlot < 0 || noSlot >= size) {
                throw new IllegalArgumentException("Yes/No slots out of bounds");
            }
            if (yesSlot == noSlot) throw new IllegalArgumentException("Yes/No slots must be distinct");
            validateNoCollisionsWithBackAndFixed(java.util.Map.of(), size, backButton, backSlot, java.util.Set.of(yesSlot, noSlot), "ConfirmationMenu");
            org.bukkit.inventory.ItemStack effectiveFiller = fillerExplicitlySet ? filler : GuiItemHelper.filler();
            return new ConfirmationMenu(gui, title, size, effectiveFiller, question, onConfirm, onCancel, yesSlot, noSlot, backButton, backSlot);
        }
    }
}
