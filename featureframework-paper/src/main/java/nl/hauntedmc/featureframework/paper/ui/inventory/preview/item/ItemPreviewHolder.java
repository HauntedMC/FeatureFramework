package nl.hauntedmc.featureframework.paper.ui.inventory.preview.item;

import nl.hauntedmc.featureframework.paper.ui.inventory.preview.PreviewHolder;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Marker holder for preview windows so listeners can protect them.
 */
public record ItemPreviewHolder(ItemStack snapshot) implements PreviewHolder {

    @Override
    public Inventory getInventory() {
        // Bukkit supplies Inventory instance; we just mark the holder type.
        return null;
    }
}
