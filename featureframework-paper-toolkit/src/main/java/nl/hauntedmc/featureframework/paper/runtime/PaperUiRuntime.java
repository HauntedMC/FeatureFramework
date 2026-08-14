package nl.hauntedmc.featureframework.paper.runtime;

import nl.hauntedmc.featureframework.lifecycle.CleanupSequence;
import nl.hauntedmc.featureframework.paper.ui.hud.actionbar.ActionBars;
import nl.hauntedmc.featureframework.paper.ui.hud.actionbar.impl.PaperActionBarService;
import nl.hauntedmc.featureframework.paper.ui.hud.scoreboard.ScoreboardListener;
import nl.hauntedmc.featureframework.paper.ui.hud.scoreboard.ScoreboardManager;
import nl.hauntedmc.featureframework.paper.ui.inventory.preview.PreviewUIListener;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.event.HandlerList;

import java.util.Objects;

/** Owns the shared Paper UI listeners and global HUD service for one host plugin. */
public final class PaperUiRuntime {
    private final JavaPlugin plugin;
    private PaperActionBarService actionBars;
    private ScoreboardListener scoreboardListener;
    private PreviewUIListener previewListener;

    public PaperUiRuntime(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    public synchronized void start() {
        if (actionBars != null) return;
        PluginManager plugins = plugin.getServer().getPluginManager();
        ScoreboardListener scoreboards = new ScoreboardListener();
        PreviewUIListener previews = new PreviewUIListener();
        PaperActionBarService service = null;
        boolean scoreboardsRegistered = false;
        boolean previewsRegistered = false;
        boolean actionBarsPublished = false;
        try {
            plugins.registerEvents(scoreboards, plugin);
            scoreboardsRegistered = true;
            plugins.registerEvents(previews, plugin);
            previewsRegistered = true;
            try {
                ScoreboardManager.initializeOnlinePlayers(plugin.getLogger());
            } catch (Throwable failure) {
                plugin.getLogger().warning("Scoreboard init error: " + failure.getMessage());
            }
            service = new PaperActionBarService(plugin);
            ActionBars.bootstrap(service);
            actionBarsPublished = true;
            scoreboardListener = scoreboards;
            previewListener = previews;
            actionBars = service;
        } catch (Throwable failure) {
            actionBars = null;
            scoreboardListener = null;
            previewListener = null;
            rollbackStart(failure, scoreboards, scoreboardsRegistered, previews, previewsRegistered,
                    service, actionBarsPublished);
            throwUnchecked(failure);
        }
    }

    public synchronized void stop() {
        PaperActionBarService service = actionBars;
        ScoreboardListener scoreboards = scoreboardListener;
        PreviewUIListener previews = previewListener;
        actionBars = null;
        scoreboardListener = null;
        previewListener = null;
        CleanupSequence.run(
                () -> {
                    if (scoreboards != null) HandlerList.unregisterAll(scoreboards);
                    if (previews != null) HandlerList.unregisterAll(previews);
                },
                () -> ScoreboardManager.cleanupOnlinePlayers(plugin.getLogger()),
                () -> {
                    if (service != null) service.shutdown();
                },
                () -> {
                    if (service != null) ActionBars.unpublish(service);
                }
        );
    }

    private void rollbackStart(
            Throwable failure,
            ScoreboardListener scoreboards,
            boolean scoreboardsRegistered,
            PreviewUIListener previews,
            boolean previewsRegistered,
            PaperActionBarService service,
            boolean actionBarsPublished
    ) {
        rollback(failure, () -> {
            if (previewsRegistered) HandlerList.unregisterAll(previews);
        });
        rollback(failure, () -> {
            if (scoreboardsRegistered) HandlerList.unregisterAll(scoreboards);
        });
        rollback(failure, () -> ScoreboardManager.cleanupOnlinePlayers(plugin.getLogger()));
        rollback(failure, () -> {
            if (service != null) service.shutdown();
        });
        rollback(failure, () -> {
            if (actionBarsPublished && service != null) ActionBars.unpublish(service);
        });
    }

    private static void rollback(Throwable failure, Runnable step) {
        try {
            step.run();
        } catch (Throwable cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
        }
    }

    @SuppressWarnings("unchecked")
    private static <E extends Throwable> void throwUnchecked(Throwable failure) throws E {
        throw (E) failure;
    }
}
