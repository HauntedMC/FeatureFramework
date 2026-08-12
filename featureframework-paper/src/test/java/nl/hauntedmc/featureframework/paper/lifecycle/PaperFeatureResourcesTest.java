package nl.hauntedmc.featureframework.paper.lifecycle;

import nl.hauntedmc.featureframework.api.feature.FeatureId;
import nl.hauntedmc.featureframework.integration.dataprovider.FeatureDataManager;
import nl.hauntedmc.featureframework.lifecycle.FeatureCacheManager;
import nl.hauntedmc.featureframework.paper.command.brigadier.BrigadierDispatcher;
import nl.hauntedmc.featureframework.paper.command.FeatureCommandManager;
import nl.hauntedmc.featureframework.paper.ui.inventory.menu.FeatureGUIManager;
import nl.hauntedmc.featureframework.service.DefaultCapabilityRegistry;
import nl.hauntedmc.featureframework.service.FeatureServiceManager;
import nl.hauntedmc.featureframework.service.InternalServiceRegistry;
import nl.hauntedmc.featureframework.toolkit.log.FrameworkLogger;
import org.bukkit.Server;
import org.bukkit.command.CommandMap;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaperFeatureResourcesTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void dataProviderFactoryOwnsPlatformDiscoveryAndSupportsAnUnavailablePlugin() {
        Plugin plugin = mock(Plugin.class);
        Server server = mock(Server.class);
        PluginManager pluginManager = mock(PluginManager.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getCommandMap()).thenReturn(mock(CommandMap.class));
        when(server.getPluginManager()).thenReturn(pluginManager);

        PaperFeatureResources<FeatureDataManager> available = PaperFeatureResourcesFactory.withDataProvider(
                        plugin,
                        temporaryDirectory,
                        mock(BrigadierDispatcher.class),
                        () -> false,
                        FrameworkLogger.noop(),
                        () -> true,
                        () -> "validate")
                .create("TestFeature", capabilities(), new InternalServiceRegistry<>());
        PaperFeatureResources<Void> unavailable = PaperFeatureResourcesFactory.withoutDataProvider(
                        plugin,
                        temporaryDirectory,
                        mock(BrigadierDispatcher.class),
                        () -> false,
                        FrameworkLogger.noop())
                .create("TestFeature", capabilities(), new InternalServiceRegistry<>());

        assertInstanceOf(FeatureDataManager.class, available.getDataManager());
        assertThrows(IllegalStateException.class, unavailable::getDataManager);
    }

    @Test
    void cleanupAttemptsEveryPlatformResourceAndAggregatesFailures() {
        FeatureTaskManager tasks = mock();
        FeatureCommandManager commands = mock();
        FeatureListenerManager listeners = mock();
        FeatureCacheManager caches = mock();
        FeatureGUIManager gui = mock();
        FeatureServiceManager<FeatureId> services = mock();
        IllegalStateException first = new IllegalStateException("gui");
        IllegalArgumentException second = new IllegalArgumentException("listeners");
        doThrow(first).when(gui).shutdown();
        doThrow(second).when(listeners).unregisterAllListeners();
        PaperFeatureResources<Void> resources = new PaperFeatureResources<>(
                tasks, commands, listeners, null, () -> { }, () -> { }, caches, gui, services);

        RuntimeException thrown = assertThrows(RuntimeException.class, resources::cleanup);

        assertSame(first, thrown);
        assertEquals(1, thrown.getSuppressed().length);
        assertSame(second, thrown.getSuppressed()[0]);
        verify(tasks).cancelAllTasks();
        verify(commands).unregisterAllBrigadierCommands();
        verify(services).unregisterAllServices();
        verify(caches).cleanupAll();
    }

    private DefaultCapabilityRegistry capabilities() {
        return new DefaultCapabilityRegistry(getClass().getPackageName(), getClass().getClassLoader());
    }
}
