package nl.hauntedmc.featureframework.paper.ui.inventory.preview;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

/**
 * Cancels moving/taking items inside any preview window (item, shulker, full inventory).
 * Includes creative cancel to prevent middle-click cloning etc.
 */
public final class PreviewUIListener implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof PreviewHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof PreviewHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCreative(InventoryCreativeEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof PreviewHolder) {
            event.setCancelled(true);
        }
    }
}
