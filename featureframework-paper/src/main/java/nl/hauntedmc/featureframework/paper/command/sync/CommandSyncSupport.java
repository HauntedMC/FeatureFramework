package nl.hauntedmc.featureframework.paper.command.sync;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.function.Consumer;

final class CommandSyncSupport {

    private CommandSyncSupport() {
    }

    static void trySyncCommands(Plugin plugin, Consumer<String> warningLogger) {
        try {
            Method m = plugin.getServer().getClass().getMethod("syncCommands");
            m.setAccessible(true);
            m.invoke(plugin.getServer());
        } catch (NoSuchMethodException ignored) {
            // Older implementations may not support syncCommands.
        } catch (Throwable t) {
            warningLogger.accept("syncCommands() failed: " + t.getMessage());
        }
    }

    static void updatePlayersCommands(Iterable<? extends Player> players, Consumer<String> warningLogger) {
        for (Player p : players) {
            try {
                p.updateCommands();
            } catch (Throwable t) {
                warningLogger.accept("Failed to update commands for " + p.getName() + ": " + t.getMessage());
            }
        }
    }
}
