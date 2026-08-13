package nl.hauntedmc.featureframework.paper.host;

import nl.hauntedmc.dataregistry.api.DataRegistryApi;
import nl.hauntedmc.featureframework.api.feature.FeatureId;
import nl.hauntedmc.featureframework.api.service.CapabilityRegistry;
import nl.hauntedmc.featureframework.config.FeatureConfigHandler;
import nl.hauntedmc.featureframework.loader.FeatureDescriptor;
import nl.hauntedmc.featureframework.paper.lifecycle.PaperFeatureResources;
import nl.hauntedmc.featureframework.paper.lifecycle.FeatureTaskManager;
import nl.hauntedmc.featureframework.paper.localization.PaperLocalization;
import nl.hauntedmc.featureframework.paper.log.FeatureLogger;
import nl.hauntedmc.featureframework.service.FeatureServiceManager;
import nl.hauntedmc.featureframework.service.InternalServiceRegistry;
import nl.hauntedmc.featureframework.toolkit.io.config.ConfigMap;
import nl.hauntedmc.featureframework.toolkit.io.localization.MessageMap;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaperFeatureContextTest {

    @Test
    void exposesTypedPlatformResourcesAndTheirServiceManager() {
        Plugin plugin = mock();
        FeatureDescriptor<?, ?> descriptor = mock();
        FeatureConfigHandler config = mock();
        PaperLocalization localization = mock();
        PaperFeatureResources<String> resources = mock();
        FeatureLogger logger = mock();
        CapabilityRegistry capabilities = mock();
        InternalServiceRegistry<FeatureId> internalServices = mock();
        FeatureServiceManager<FeatureId> services = mock();
        when(resources.getApiManager()).thenReturn(services);

        PaperFeatureContext<Plugin, String> context = new PaperFeatureContext<>(
                plugin, descriptor, config, localization, resources, logger, capabilities, internalServices);

        assertSame(plugin, context.plugin());
        assertSame(resources, context.resources());
        assertSame(localization, context.localization());
        assertSame(services, context.services());
    }

    @Test
    void dataRegistryFeatureOwnsPlatformGateAndCanonicalAccessors() {
        Plugin plugin = mock();
        DataRegistryApi dataRegistry = mock();
        FeatureDescriptor<?, ?> descriptor = mock();
        FeatureConfigHandler config = mock();
        PaperLocalization localization = mock();
        PaperFeatureResources<String> resources = mock();
        FeatureTaskManager tasks = mock();
        FeatureLogger logger = mock();
        CapabilityRegistry capabilities = mock();
        InternalServiceRegistry<FeatureId> internalServices = mock();
        FeatureServiceManager<FeatureId> services = mock();
        when(resources.getApiManager()).thenReturn(services);
        when(resources.getTaskManager()).thenReturn(tasks);
        when(plugin.isEnabled()).thenReturn(true);
        PaperFeatureContext<Plugin, String> context = new PaperFeatureContext<>(
                plugin, descriptor, config, localization, resources, logger, capabilities,
                internalServices, () -> dataRegistry);
        TestDataRegistryFeature feature = new TestDataRegistryFeature(context);
        Runnable continuation = () -> { };

        assertSame(plugin, feature.plugin());
        assertSame(logger, feature.logger());
        assertSame(resources, feature.resources());
        assertSame(localization, feature.localization());
        assertSame(dataRegistry, feature.dataRegistry());
        assertTrue(feature.hostAvailable());
        feature.scheduleContinuation(continuation);
        feature.warn("warning");

        verify(tasks).scheduleOneTimeTask(continuation);
        verify(logger).warning("warning");
    }

    private static final class TestDataRegistryFeature extends PaperDataRegistryFeature<Plugin, String> {
        private TestDataRegistryFeature(
                PaperFeatureContext<Plugin, String> context
        ) {
            super(context);
        }

        @Override public ConfigMap getDefaultConfig() { return new ConfigMap(); }
        @Override public MessageMap getDefaultMessages() { return new MessageMap(); }
        @Override public void initialize() { }
        @Override public void disable() { }
    }
}
