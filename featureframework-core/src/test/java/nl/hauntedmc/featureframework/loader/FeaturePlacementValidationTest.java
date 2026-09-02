package nl.hauntedmc.featureframework.loader;

import nl.hauntedmc.featureframework.api.feature.FeaturePlacement;
import nl.hauntedmc.featureframework.api.feature.FeatureStartupPhase;
import nl.hauntedmc.featureframework.api.feature.FeatureScope;
import nl.hauntedmc.featureframework.feature.Feature;
import nl.hauntedmc.featureframework.toolkit.io.config.ConfigMap;
import nl.hauntedmc.featureframework.toolkit.io.localization.MessageMap;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FeaturePlacementValidationTest {
    @Test
    void allNodesCannotRequireLeaderOnlyFeature() {
        Definition leader = definition("ingress", FeaturePlacement.GROUP_LEADER_ONLY, Set.of(), Set.of());
        Definition consumer = definition("commands", FeaturePlacement.ALL_NODES, Set.of("ingress"), Set.of());

        assertThrows(IllegalStateException.class,
                () -> FeatureManifestDiscovery.discover(List.of(leader, consumer), Set.of(), "demo"));
    }

    @Test
    void leaderOnlyMayRequireAllNodesOrLeaderOnlyFeatures() {
        Definition shared = definition("shared", FeaturePlacement.ALL_NODES, Set.of(), Set.of());
        Definition ingress = definition("ingress", FeaturePlacement.GROUP_LEADER_ONLY, Set.of(), Set.of());
        Definition singleton = definition(
                "singleton", FeaturePlacement.GROUP_LEADER_ONLY, Set.of("shared", "ingress"), Set.of());

        assertDoesNotThrow(() -> FeatureManifestDiscovery.discover(
                List.of(shared, ingress, singleton), Set.of(), "demo"));
    }

    @Test
    void optionalCrossPlacementDependencyDoesNotInvalidateAllNodesFeature() {
        Definition leader = definition("ingress", FeaturePlacement.GROUP_LEADER_ONLY, Set.of(), Set.of());
        Definition consumer = definition(
                "commands", FeaturePlacement.ALL_NODES, Set.of(), Set.of("ingress"));

        assertDoesNotThrow(() -> FeatureManifestDiscovery.discover(
                List.of(leader, consumer), Set.of(), "demo"));
    }

    private static Definition definition(
            String name,
            FeaturePlacement placement,
            Set<String> required,
            Set<String> optional
    ) {
        return new Definition(name, placement, required, optional);
    }

    private record Definition(
            String featureName,
            FeaturePlacement placement,
            Set<String> required,
            Set<String> optional
    ) implements FeatureManifestDefinition<ResolvedFeatureDefinition<TestFeature, Object>> {
        @Override public FeatureStartupPhase startupPhase() { return FeatureStartupPhase.CORE; }
        @Override public FeatureScope scope() { return FeatureScope.NODE; }

        @Override
        public ResolvedFeatureDefinition<TestFeature, Object> descriptor(Set<String> discovered) {
            LinkedHashSet<String> dependencies = new LinkedHashSet<>(required);
            dependencies.addAll(discovered);
            return new ResolvedFeatureDefinition<>(
                    featureName, featureName, "1", TestFeature.class, ignored -> new TestFeature(),
                    dependencies, optional, Set.of(), Set.of(), Set.of(), placement);
        }

        @Override public Set<Class<?>> requiredCapabilities() { return Set.of(); }
        @Override public Set<Class<?>> optionalCapabilities() { return Set.of(); }
        @Override public Set<Class<?>> providedCapabilities() { return Set.of(); }
        @Override public Set<Class<?>> requiredInternalServices() { return Set.of(); }
        @Override public Set<Class<?>> optionalInternalServices() { return Set.of(); }
        @Override public Set<Class<?>> providedInternalServices() { return Set.of(); }
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
