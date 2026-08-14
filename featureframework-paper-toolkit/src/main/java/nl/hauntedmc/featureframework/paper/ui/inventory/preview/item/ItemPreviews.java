package nl.hauntedmc.featureframework.paper.ui.inventory.preview.item;

import net.kyori.adventure.text.Component;
import nl.hauntedmc.featureframework.paper.lifecycle.FeatureTaskManager;
import org.bukkit.Bukkit;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;

import java.util.Objects;

/**
 * Simple API for opening preview GUIs.
 */
public final class ItemPreviews {

    private ItemPreviews() {
    }

    /**
     * Returns true if the given item is any shulker box item (incl. colored variants).
     */
    public static boolean isShulkerBoxItem(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) return false;
        if (!(stack.getItemMeta() instanceof BlockStateMeta bsm)) return false;
        return bsm.getBlockState() instanceof ShulkerBox;
    }

    /**
     * Opens a 3x3 preview window (Dispenser-style) with the snapshot in the center.
     */
    public static void open3x3Preview(
            FeatureTaskManager tasks,
            Player viewer,
            ItemStack snapshot,
            Component title
    ) {
        FeatureTaskManager taskManager = Objects.requireNonNull(tasks, "tasks");
        Player target = Objects.requireNonNull(viewer, "viewer");
        Component inventoryTitle = Objects.requireNonNull(title, "title");
        taskManager.scheduleOneTimeTask(() -> open3x3Now(target, snapshot, inventoryTitle));
    }

    /**
     * Opens a 27-slot shulker preview window with the contents of the shulker snapshot.
     * Falls back to 3x3 if the item isn't a valid shulker box.
     */
    public static void openShulkerPreview(
            FeatureTaskManager tasks,
            Player viewer,
            ItemStack shulkerItem,
            Component fallbackTitle
    ) {
        FeatureTaskManager taskManager = Objects.requireNonNull(tasks, "tasks");
        Player target = Objects.requireNonNull(viewer, "viewer");
        Component title = Objects.requireNonNull(fallbackTitle, "fallbackTitle");
        taskManager.scheduleOneTimeTask(() -> openShulkerNow(target, shulkerItem, title));
    }

    private static void openShulkerNow(Player viewer, ItemStack shulkerItem, Component fallbackTitle) {
        ItemStack copy = copyOf(shulkerItem);
        if (copy == null || !(copy.getItemMeta() instanceof BlockStateMeta bsm)) {
            open3x3Now(viewer, shulkerItem, fallbackTitle);
            return;
        }
        if (!(bsm.getBlockState() instanceof ShulkerBox sbState)) {
            open3x3Now(viewer, shulkerItem, fallbackTitle);
            return;
        }

        final Component title = resolveItemDisplayTitle(copy, fallbackTitle);
        final Inventory inv = Bukkit.createInventory(new ItemPreviewHolder(copy), InventoryType.SHULKER_BOX, title);

        // Clone contents defensively; never touch the real item/NBT.
        ItemStack[] src = sbState.getInventory().getContents();
        ItemStack[] dst = new ItemStack[inv.getSize()];
        for (int i = 0; i < Math.min(src.length, dst.length); i++) {
            ItemStack it = src[i];
            dst[i] = (it == null || it.getType().isAir()) ? null : it.clone();
        }
        inv.setContents(dst);

        viewer.openInventory(inv);
    }

    private static void open3x3Now(Player viewer, ItemStack snapshot, Component title) {
        ItemStack copy = copyOf(snapshot);
        Inventory inventory = Bukkit.createInventory(
                new ItemPreviewHolder(copy),
                InventoryType.DISPENSER,
                title
        );
        if (copy != null) {
            inventory.setItem(4, copy);
        }
        viewer.openInventory(inventory);
    }

    private static ItemStack copyOf(ItemStack item) {
        return item == null || item.getType().isAir() ? null : item.clone();
    }

    /**
     * If the item has a custom display name (Paper API), use it as the GUI title; otherwise use the provided fallback.
     */
    private static Component resolveItemDisplayTitle(ItemStack stack, Component fallback) {
        if (stack == null || stack.getType().isAir()) return fallback;
        var meta = stack.getItemMeta();
        if (meta == null) return fallback;

        if (meta.hasDisplayName()) {
            Component name = meta.displayName();
            if (name != null) return name;
        }
        return fallback;
    }
}
