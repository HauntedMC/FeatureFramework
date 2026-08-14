package nl.hauntedmc.featureframework.paper.lifecycle;

import nl.hauntedmc.featureframework.api.feature.FeatureId;
import nl.hauntedmc.featureframework.lifecycle.FeatureCacheManager;
import nl.hauntedmc.featureframework.paper.command.FeatureCommandManager;
import nl.hauntedmc.featureframework.resource.FeatureResourceExtensions;
import nl.hauntedmc.featureframework.resource.FeatureResourceOwner;
import nl.hauntedmc.featureframework.service.FeatureServiceManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PaperFeatureResourcesTest {

    @Test
    void exposesTheCompleteBasePlatformResourceScope() {
        FeatureTaskManager tasks = mock();
        FeatureCommandManager commands = mock();
        FeatureListenerManager listeners = mock();
        FeatureCacheManager caches = mock();
        FeatureServiceManager<FeatureId> services = mock();
        FeatureResourceOwner ownership = new FeatureResourceOwner();
        FeatureResourceExtensions extensions = new FeatureResourceExtensions();

        PaperFeatureResources resources = new PaperFeatureResources(
                tasks, commands, listeners, caches, services, ownership, extensions);

        assertSame(tasks, resources.tasks());
        assertSame(commands, resources.commands());
        assertSame(listeners, resources.listeners());
        assertSame(caches, resources.caches());
        assertSame(services, resources.capabilities());
        assertSame(ownership, resources.ownership());
        assertSame(extensions, resources.extensions());
    }

    @Test
    void cleanupAttemptsEveryPlatformResourceAndAggregatesFailures() {
        FeatureTaskManager tasks = mock();
        FeatureCommandManager commands = mock();
        FeatureListenerManager listeners = mock();
        FeatureCacheManager caches = mock();
        FeatureServiceManager<FeatureId> services = mock();
        FeatureResourceOwner ownership = new FeatureResourceOwner();
        IllegalStateException first = new IllegalStateException("listeners");
        IllegalArgumentException second = new IllegalArgumentException("ownership");
        doThrow(first).when(listeners).unregisterAllListeners();
        ownership.own("owned", ignored -> { throw second; });
        PaperFeatureResources resources = new PaperFeatureResources(
                tasks, commands, listeners, caches, services, ownership, new FeatureResourceExtensions());

        RuntimeException thrown = assertThrows(RuntimeException.class, resources::cleanup);

        assertSame(first, thrown);
        assertEquals(1, thrown.getSuppressed().length);
        assertSame(second, thrown.getSuppressed()[0]);
        verify(tasks).cancelAllTasks();
        verify(commands).unregisterAllBrigadierCommands();
        verify(services).unregisterAllServices();
        verify(caches).cleanupAll();
    }
}
