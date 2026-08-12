package nl.hauntedmc.featureframework.paper.ui.inventory.preview.inv;

import nl.hauntedmc.featureframework.paper.ui.inventory.preview.PreviewHolder;
import org.bukkit.inventory.Inventory;

/**
 * Marker holder for inventory preview windows.
 */
public record InventoryPreviewHolder(InventorySnapshot snapshot) implements PreviewHolder {

    @Override
    public Inventory getInventory() {
        // Bukkit provides the inventory instance.
        return null;
    }
}
