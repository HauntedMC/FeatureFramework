package nl.hauntedmc.featureframework.paper.host;

import nl.hauntedmc.featureframework.api.feature.FeatureId;
import nl.hauntedmc.featureframework.api.service.CapabilityRegistry;
import nl.hauntedmc.featureframework.config.FeatureConfigHandler;
import nl.hauntedmc.featureframework.feature.Feature;
import nl.hauntedmc.featureframework.loader.ResolvedFeatureDefinition;
import nl.hauntedmc.featureframework.paper.lifecycle.PaperFeatureResources;
import nl.hauntedmc.featureframework.paper.localization.PaperLocalization;
import nl.hauntedmc.featureframework.paper.log.FeatureLogger;
import nl.hauntedmc.featureframework.resource.FeatureResourceExtensions;
import nl.hauntedmc.featureframework.resource.ResourceKey;
import nl.hauntedmc.featureframework.service.FeatureServiceManager;
import nl.hauntedmc.featureframework.service.InternalServiceRegistry;
import nl.hauntedmc.featureframework.toolkit.io.config.ConfigService;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PaperFeatureContextTest {

    @Test
    void exposesTypedPlatformResourcesAndSharedServices() {
        Plugin plugin = mock();
        FeatureConfigHandler config = mock();
        PaperLocalization localization = mock();
        PaperFeatureResources resources = mock();
        FeatureLogger logger = mock();
        CapabilityRegistry capabilities = mock();
        InternalServiceRegistry<FeatureId> internalServices = mock();
        FeatureServiceManager<FeatureId> services = mock();
        ConfigService files = mock();
        when(resources.capabilities()).thenReturn(services);
        when(resources.extensions()).thenReturn(new FeatureResourceExtensions());

        PaperFeatureContext<Plugin> context = new PaperFeatureContext<>(
                plugin, definition(Set.of()), config, localization, resources, logger,
                capabilities, internalServices, files);

        assertSame(plugin, context.plugin());
        assertSame(resources, context.resources());
        assertSame(localization, context.localization());
        assertSame(services, context.services());
        assertSame(files, context.files());
    }

    @Test
    void rejectsAFeatureWhoseRequiredResourceExtensionIsUnavailable() {
        PaperFeatureResources resources = mock();
        when(resources.capabilities()).thenReturn(mock());
        when(resources.extensions()).thenReturn(new FeatureResourceExtensions());

        assertThrows(IllegalStateException.class, () -> context(resources, definition(Set.of(TestExtension.class))));
    }

    @Test
    void acceptsAFeatureWhoseRequiredResourceExtensionWasContributed() {
        FeatureResourceExtensions extensions = new FeatureResourceExtensions();
        extensions.register(ResourceKey.of(TestExtension.class), new TestExtension());
        PaperFeatureResources resources = mock();
        when(resources.capabilities()).thenReturn(mock());
        when(resources.extensions()).thenReturn(extensions);

        PaperFeatureContext<Plugin> context = context(resources, definition(Set.of(TestExtension.class)));

        assertSame(extensions, context.resources().extensions());
    }

    private static PaperFeatureContext<Plugin> context(
            PaperFeatureResources resources,
            ResolvedFeatureDefinition<?, ?> definition
    ) {
        return new PaperFeatureContext<>(
                mock(), definition, mock(), mock(), resources, mock(), mock(), mock(), mock());
    }

    private static ResolvedFeatureDefinition<Feature, Object> definition(Set<Class<?>> requiredExtensions) {
        return new ResolvedFeatureDefinition<>(
                "test", "Test", "1.0.0", Feature.class, ignored -> mock(Feature.class),
                Set.of(), Set.of(), Set.of(), requiredExtensions, Set.of());
    }

    private static final class TestExtension {
    }
}
