package nl.hauntedmc.featureframework.paper.ui.hud.scoreboard;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;

public class ScoreboardListener implements Listener {

    @EventHandler(priority = EventPriority.LOW)
    public void onPlayerJoin(@NotNull PlayerJoinEvent event) {
        ScoreboardManager.onPlayerJoin(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerQuit(@NotNull PlayerQuitEvent event) {
        ScoreboardManager.onPlayerQuit(event.getPlayer());
    }

}
