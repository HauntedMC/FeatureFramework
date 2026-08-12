package nl.hauntedmc.featureframework.paper.runtime;

import nl.hauntedmc.featureframework.lifecycle.CleanupSequence;
import nl.hauntedmc.featureframework.paper.ui.hud.actionbar.ActionBars;
import nl.hauntedmc.featureframework.paper.ui.hud.actionbar.impl.PaperActionBarService;
import nl.hauntedmc.featureframework.paper.ui.hud.scoreboard.ScoreboardListener;
import nl.hauntedmc.featureframework.paper.ui.hud.scoreboard.ScoreboardManager;
import nl.hauntedmc.featureframework.paper.ui.inventory.preview.PreviewUIListener;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

/** Owns the shared Paper UI listeners and global HUD service for one host plugin. */
public final class PaperUiRuntime {
    private final JavaPlugin plugin;
    private PaperActionBarService actionBars;

    public PaperUiRuntime(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    public synchronized void start() {
        if (actionBars != null) return;
        PluginManager plugins = plugin.getServer().getPluginManager();
        plugins.registerEvents(new ScoreboardListener(), plugin);
        plugins.registerEvents(new PreviewUIListener(), plugin);
        try {
            ScoreboardManager.initializeOnlinePlayers(plugin.getLogger());
        } catch (Throwable failure) {
            plugin.getLogger().warning("Scoreboard init error: " + failure.getMessage());
        }
        PaperActionBarService service = new PaperActionBarService(plugin);
        ActionBars.bootstrap(service);
        actionBars = service;
    }

    public synchronized void stop() {
        PaperActionBarService service = actionBars;
        actionBars = null;
        CleanupSequence.run(
                () -> ScoreboardManager.cleanupOnlinePlayers(plugin.getLogger()),
                () -> {
                    if (service != null) service.shutdown();
                },
                () -> {
                    if (service != null) ActionBars.unpublish(service);
                }
        );
    }
}
