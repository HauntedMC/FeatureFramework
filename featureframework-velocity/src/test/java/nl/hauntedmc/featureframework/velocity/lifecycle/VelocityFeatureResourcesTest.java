package nl.hauntedmc.featureframework.velocity.lifecycle;

import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.event.EventManager;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.scheduler.Scheduler;
import nl.hauntedmc.featureframework.api.feature.FeatureId;
import nl.hauntedmc.featureframework.integration.dataprovider.FeatureDataManager;
import nl.hauntedmc.featureframework.lifecycle.FeatureCacheManager;
import nl.hauntedmc.featureframework.service.DefaultCapabilityRegistry;
import nl.hauntedmc.featureframework.service.FeatureServiceManager;
import nl.hauntedmc.featureframework.service.InternalServiceRegistry;
import nl.hauntedmc.featureframework.toolkit.log.FrameworkLogger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VelocityFeatureResourcesTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void dataProviderFactoryOwnsPlatformDiscovery() {
        Object plugin = new Object();
        ProxyServer proxy = mock(ProxyServer.class);
        when(proxy.getScheduler()).thenReturn(mock(Scheduler.class));
        when(proxy.getCommandManager()).thenReturn(mock(CommandManager.class));
        when(proxy.getEventManager()).thenReturn(mock(EventManager.class));

        VelocityFeatureResources<FeatureDataManager> resources =
                VelocityFeatureResourcesFactory.withDataProvider(
                                plugin,
                                proxy,
                                mock(Logger.class),
                                temporaryDirectory,
                                FrameworkLogger.noop(),
                                () -> "validate")
                        .create("TestFeature", capabilities(), new InternalServiceRegistry<>());
        VelocityFeatureResources<Void> withoutData =
                VelocityFeatureResourcesFactory.withoutDataProvider(
                                plugin,
                                proxy,
                                mock(Logger.class),
                                temporaryDirectory,
                                FrameworkLogger.noop())
                        .create("TestFeature", capabilities(), new InternalServiceRegistry<>());

        assertInstanceOf(FeatureDataManager.class, resources.getDataManager());
        assertThrows(IllegalStateException.class, withoutData::getDataManager);
    }

    @Test
    void cleanupAttemptsEveryPlatformResourceAndAggregatesFailures() {
        FeatureTaskManager tasks = mock();
        FeatureCommandManager commands = mock();
        FeatureListenerManager listeners = mock();
        FeatureCacheManager caches = mock();
        FeatureServiceManager<FeatureId> services = mock();
        IllegalStateException first = new IllegalStateException("listeners");
        IllegalArgumentException second = new IllegalArgumentException("tasks");
        doThrow(first).when(listeners).unregisterAllListeners();
        doThrow(second).when(tasks).cancelAllTasks();
        VelocityFeatureResources<Void> resources = new VelocityFeatureResources<>(
                tasks, commands, listeners, null, () -> { }, () -> { }, caches, services);

        RuntimeException thrown = assertThrows(RuntimeException.class, resources::cleanup);

        assertSame(first, thrown);
        assertEquals(1, thrown.getSuppressed().length);
        assertSame(second, thrown.getSuppressed()[0]);
        verify(commands).unregisterAllBrigadierCommands();
        verify(services).unregisterAllServices();
        verify(caches).cleanupAll();
    }

    private DefaultCapabilityRegistry capabilities() {
        return new DefaultCapabilityRegistry(getClass().getPackageName(), getClass().getClassLoader());
    }
}
