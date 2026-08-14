package nl.hauntedmc.featureframework.paper.runtime;

import nl.hauntedmc.featureframework.paper.ui.hud.actionbar.ActionBarService;
import nl.hauntedmc.featureframework.paper.ui.hud.actionbar.ActionBars;
import nl.hauntedmc.featureframework.paper.ui.hud.scoreboard.ScoreboardManager;
import nl.hauntedmc.featureframework.paper.ui.inventory.preview.PreviewUIListener;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaperUiRuntimeTest {

    @Test
    void repeatedStartAndStopRegistersAndUnregistersEachListenerGeneration() {
        JavaPlugin plugin = plugin();
        PluginManager plugins = plugin.getServer().getPluginManager();

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
             MockedStatic<HandlerList> handlers = mockStatic(HandlerList.class);
             MockedStatic<ScoreboardManager> scoreboards = mockStatic(ScoreboardManager.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            PaperUiRuntime runtime = new PaperUiRuntime(plugin);

            runtime.start();
            assertNotNull(ActionBars.service());
            runtime.stop();
            runtime.start();
            runtime.stop();

            verify(plugins, times(4)).registerEvents(any(Listener.class), same(plugin));
            handlers.verify(() -> HandlerList.unregisterAll(any(Listener.class)), times(4));
            scoreboards.verify(() -> ScoreboardManager.initializeOnlinePlayers(plugin.getLogger()), times(2));
            scoreboards.verify(() -> ScoreboardManager.cleanupOnlinePlayers(plugin.getLogger()), times(2));
            assertThrows(IllegalStateException.class, ActionBars::service);
        }
    }

    @Test
    void failedActionBarPublicationRollsBackListenersAndAllowsRetry() {
        JavaPlugin plugin = plugin();
        PluginManager plugins = plugin.getServer().getPluginManager();
        ActionBarService existingService = mock(ActionBarService.class);
        ActionBars.bootstrap(existingService);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
             MockedStatic<HandlerList> handlers = mockStatic(HandlerList.class);
             MockedStatic<ScoreboardManager> scoreboards = mockStatic(ScoreboardManager.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            PaperUiRuntime runtime = new PaperUiRuntime(plugin);

            assertThrows(IllegalStateException.class, runtime::start);
            handlers.verify(() -> HandlerList.unregisterAll(any(Listener.class)), times(2));
            scoreboards.verify(() -> ScoreboardManager.cleanupOnlinePlayers(plugin.getLogger()));

            ActionBars.unpublish(existingService);
            runtime.start();
            runtime.stop();

            verify(plugins, times(4)).registerEvents(any(Listener.class), same(plugin));
            handlers.verify(() -> HandlerList.unregisterAll(any(Listener.class)), times(4));
            assertThrows(IllegalStateException.class, ActionBars::service);
        }
    }

    @Test
    void failedSecondListenerRegistrationRollsBackTheFirstListener() {
        JavaPlugin plugin = plugin();
        PluginManager plugins = plugin.getServer().getPluginManager();
        doThrow(new IllegalStateException("preview registration failed"))
                .when(plugins).registerEvents(isA(PreviewUIListener.class), same(plugin));

        try (MockedStatic<HandlerList> handlers = mockStatic(HandlerList.class);
             MockedStatic<ScoreboardManager> scoreboards = mockStatic(ScoreboardManager.class)) {
            PaperUiRuntime runtime = new PaperUiRuntime(plugin);

            assertThrows(IllegalStateException.class, runtime::start);

            handlers.verify(() -> HandlerList.unregisterAll(any(Listener.class)));
            scoreboards.verify(() -> ScoreboardManager.cleanupOnlinePlayers(plugin.getLogger()));
            assertThrows(IllegalStateException.class, ActionBars::service);
        }
    }

    private static JavaPlugin plugin() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        Server server = mock(Server.class);
        PluginManager plugins = mock(PluginManager.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getPluginManager()).thenReturn(plugins);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("PaperUiRuntimeTest"));
        return plugin;
    }
}
