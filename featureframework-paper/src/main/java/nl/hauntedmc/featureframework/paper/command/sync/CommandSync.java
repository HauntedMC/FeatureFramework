package nl.hauntedmc.featureframework.paper.command.sync;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

public class CommandSync {
    /**
     * Actual sync work (must be called on main).
     */
    public static void apply(Plugin plugin) {
        CommandSyncSupport.trySyncCommands(plugin, msg -> plugin.getLogger().warning(msg));
        CommandSyncSupport.updatePlayersCommands(Bukkit.getOnlinePlayers(), msg -> plugin.getLogger().warning(msg));
    }
}
