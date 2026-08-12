package nl.hauntedmc.featureframework.paper.ui.inventory.menu;

import nl.hauntedmc.featureframework.paper.time.BukkitTime;
import org.bukkit.event.Listener;

/**
 * Runtime services required by interactive menus without coupling the public API to the plugin
 * framework implementation.
 */
public interface MenuRuntime extends MenuNavigator {

    void registerListener(Listener listener);

    void schedule(Runnable task);

    void scheduleDelayed(Runnable task, BukkitTime delay);
}
