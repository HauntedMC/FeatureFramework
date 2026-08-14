package nl.hauntedmc.featureframework.velocity.host;

import com.velocitypowered.api.proxy.ProxyServer;
import nl.hauntedmc.featureframework.api.feature.FeatureId;
import nl.hauntedmc.featureframework.api.service.CapabilityRegistry;
import nl.hauntedmc.featureframework.config.FeatureConfigHandler;
import nl.hauntedmc.featureframework.feature.Feature;
import nl.hauntedmc.featureframework.loader.ResolvedFeatureDefinition;
import nl.hauntedmc.featureframework.resource.FeatureResourceExtensions;
import nl.hauntedmc.featureframework.resource.ResourceKey;
import nl.hauntedmc.featureframework.service.FeatureServiceManager;
import nl.hauntedmc.featureframework.service.InternalServiceRegistry;
import nl.hauntedmc.featureframework.toolkit.io.config.ConfigService;
import nl.hauntedmc.featureframework.velocity.lifecycle.VelocityFeatureResources;
import nl.hauntedmc.featureframework.velocity.localization.VelocityLocalization;
import nl.hauntedmc.featureframework.velocity.log.FeatureLogger;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VelocityFeatureContextTest {

    @Test
    void exposesTypedPlatformResourcesAndSharedServices() {
        Object plugin = new Object();
        FeatureConfigHandler config = mock();
        VelocityLocalization localization = mock();
        VelocityFeatureResources resources = mock();
        FeatureLogger logger = mock();
        CapabilityRegistry capabilities = mock();
        InternalServiceRegistry<FeatureId> internalServices = mock();
        FeatureServiceManager<FeatureId> services = mock();
        ProxyServer proxy = mock();
        ConfigService files = mock();
        when(resources.capabilities()).thenReturn(services);
        when(resources.extensions()).thenReturn(new FeatureResourceExtensions());

        VelocityFeatureContext<Object> context = new VelocityFeatureContext<>(
                plugin, definition(Set.of()), config, localization, resources, logger,
                capabilities, internalServices, proxy, files);

        assertSame(plugin, context.plugin());
        assertSame(resources, context.resources());
        assertSame(localization, context.localization());
        assertSame(services, context.services());
        assertSame(proxy, context.proxy());
        assertSame(files, context.files());
    }

    @Test
    void rejectsAFeatureWhoseRequiredResourceExtensionIsUnavailable() {
        VelocityFeatureResources resources = mock();
        when(resources.capabilities()).thenReturn(mock());
        when(resources.extensions()).thenReturn(new FeatureResourceExtensions());

        assertThrows(IllegalStateException.class, () -> context(resources, definition(Set.of(TestExtension.class))));
    }

    @Test
    void acceptsAFeatureWhoseRequiredResourceExtensionWasContributed() {
        FeatureResourceExtensions extensions = new FeatureResourceExtensions();
        extensions.register(ResourceKey.of(TestExtension.class), new TestExtension());
        VelocityFeatureResources resources = mock();
        when(resources.capabilities()).thenReturn(mock());
        when(resources.extensions()).thenReturn(extensions);

        VelocityFeatureContext<Object> context = context(resources, definition(Set.of(TestExtension.class)));

        assertSame(extensions, context.resources().extensions());
    }

    private static VelocityFeatureContext<Object> context(
            VelocityFeatureResources resources,
            ResolvedFeatureDefinition<?, ?> definition
    ) {
        return new VelocityFeatureContext<>(
                new Object(), definition, mock(), mock(), resources, mock(), mock(), mock(), mock(), mock());
    }

    private static ResolvedFeatureDefinition<Feature, Object> definition(Set<Class<?>> requiredExtensions) {
        return new ResolvedFeatureDefinition<>(
                "test", "Test", "1.0.0", Feature.class, ignored -> mock(Feature.class),
                Set.of(), Set.of(), Set.of(), requiredExtensions, Set.of());
    }

    private static final class TestExtension {
    }
}
