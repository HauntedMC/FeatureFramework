package nl.hauntedmc.featureframework.loader;

import nl.hauntedmc.featureframework.api.feature.FeatureStartupPhase;
import nl.hauntedmc.featureframework.feature.Feature;
import nl.hauntedmc.featureframework.toolkit.io.config.ConfigMap;
import nl.hauntedmc.featureframework.toolkit.io.localization.MessageMap;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FeatureManifestDiscoveryTest {
    @Test
    void ordersIndependentFeaturesByStartupPhase() {
        Definition deferred = definition("deferred", FeatureStartupPhase.DEFERRED, Set.of(), Set.of());
        Definition foundation = definition("foundation", FeatureStartupPhase.FOUNDATION, Set.of(), Set.of());

        var result = FeatureManifestDiscovery.discover(List.of(deferred, foundation), Set.of(), "demo");

        assertEquals(List.of("foundation", "deferred"), result.discovered().stream()
                .map(item -> item.descriptor().registryName()).toList());
    }

    @Test
    void derivesDependenciesOrdersFeaturesAndProjectsPublicMetadata() {
        Definition consumer = definition("consumer", FeatureStartupPhase.CORE, Set.of(Service.class), Set.of());
        Definition provider = definition("provider", FeatureStartupPhase.SECURITY, Set.of(), Set.of(Service.class));

        var result = FeatureManifestDiscovery.discover(List.of(consumer, provider), Set.of(), "demo");

        assertEquals(List.of("provider", "consumer"), result.discovered().stream()
                .map(item -> item.descriptor().registryName()).toList());
        assertEquals(Set.of("provider"), result.discovered().get(1).descriptor().featureDependencies());
        assertEquals(Set.of("demo:service"),
                result.discovered().get(0).publicDescriptor().providedCapabilities());
    }

    @Test
    void rejectsMissingAndDuplicateProviders() {
        Definition consumer = definition("consumer", FeatureStartupPhase.SECURITY, Set.of(Service.class), Set.of());
        assertThrows(IllegalStateException.class,
                () -> FeatureManifestDiscovery.discover(List.of(consumer), Set.of(), "demo"));

        Definition first = definition("first", FeatureStartupPhase.SECURITY, Set.of(), Set.of(Service.class));
        Definition second = definition("second", FeatureStartupPhase.CORE, Set.of(), Set.of(Service.class));
        assertThrows(IllegalStateException.class,
                () -> FeatureManifestDiscovery.discover(List.of(first, second), Set.of(), "demo"));
    }

    private static Definition definition(
            String name, FeatureStartupPhase phase, Set<Class<?>> required, Set<Class<?>> provided) {
        return new Definition(name, phase, required, provided);
    }

    private record Definition(
            String featureName,
            FeatureStartupPhase startupPhase,
            Set<Class<?>> requiredCapabilities,
            Set<Class<?>> providedCapabilities
    ) implements FeatureManifestDefinition<ResolvedFeatureDefinition<TestFeature, Object>> {
        @Override
        public ResolvedFeatureDefinition<TestFeature, Object> descriptor(Set<String> dependencies) {
            return new ResolvedFeatureDefinition<>(featureName, featureName, "1", TestFeature.class,
                    ignored -> new TestFeature(), dependencies, Set.of());
        }

        @Override public Set<Class<?>> optionalCapabilities() { return Set.of(); }
        @Override public Set<Class<?>> requiredInternalServices() { return Set.of(); }
        @Override public Set<Class<?>> optionalInternalServices() { return Set.of(); }
        @Override public Set<Class<?>> providedInternalServices() { return Set.of(); }
    }

    private interface Service {
    }

    private static final class TestFeature implements Feature {
        @Override public String name() { return "test"; }
        @Override public String version() { return "1"; }
        @Override public List<String> dependencies() { return List.of(); }
        @Override public List<String> pluginDependencies() { return List.of(); }
        @Override public ConfigMap defaultConfig() { return new ConfigMap(); }
        @Override public MessageMap defaultMessages() { return new MessageMap(); }
        @Override public void initialize() { }
        @Override public void disable() { }
    }
}
