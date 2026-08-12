package nl.hauntedmc.featureframework.paper.ui.inventory.menu;

import org.bukkit.entity.Player;

/**
 * Navigation operations exposed to reusable menu implementations.
 */
public interface MenuNavigator {

    void openRoot(Player player, GuiMenu menu);

    void openChild(Player player, GuiMenu menu);

    void reopenSame(Player player, GuiMenu menu);

    boolean goBack(Player player);
}
