package nl.hauntedmc.featureframework.paper.ui.inventory.preview;

import org.bukkit.inventory.InventoryHolder;

/**
 * Marker interface for all read-only preview inventories (item, shulker, full inventory, etc.).
 * Lets listeners detect and protect preview UIs with a single instanceof check.
 */
public interface PreviewHolder extends InventoryHolder {
}
