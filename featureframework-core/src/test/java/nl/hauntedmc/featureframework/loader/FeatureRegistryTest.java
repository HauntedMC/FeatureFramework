package nl.hauntedmc.featureframework.loader;

import nl.hauntedmc.featureframework.api.feature.FeaturePlacement;
import nl.hauntedmc.featureframework.feature.Feature;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class FeatureRegistryTest {

    @Test
    void returnsDefensiveSnapshotsAndRejectsNullEntries() {
        FeatureRegistry<Feature, ResolvedFeatureDefinition<Feature, String>> registry = new FeatureRegistry<>();
        Feature feature = mock(Feature.class);
        ResolvedFeatureDefinition<Feature, String> descriptor = descriptor("demo", feature);

        registry.registerAvailableFeature(descriptor);
        registry.registerLoadedFeature("demo", feature);
        var availableSnapshot = registry.getAvailableFeatures();
        var loadedSnapshot = registry.getLoadedFeatureNames();
        registry.deregisterAvailableFeature("demo");
        registry.deregisterLoadedFeature("demo");

        assertSame(descriptor, availableSnapshot.get("demo"));
        assertEquals(Set.of("demo"), loadedSnapshot);
        assertFalse(registry.isFeatureLoaded("demo"));
        assertThrows(UnsupportedOperationException.class, () -> availableSnapshot.clear());
        assertThrows(NullPointerException.class, () -> registry.registerAvailableFeature(null));
        assertThrows(NullPointerException.class, () -> registry.registerLoadedFeature("demo", null));
    }

    @Test
    void tracksLoadedFeatures() {
        FeatureRegistry<Feature, ResolvedFeatureDefinition<Feature, String>> registry = new FeatureRegistry<>();
        Feature feature = mock(Feature.class);
        registry.registerLoadedFeature("demo", feature);

        assertTrue(registry.isFeatureLoaded("demo"));
        assertSame(feature, registry.getLoadedFeature("demo"));
        assertEquals(java.util.List.of(feature), registry.getLoadedFeatures());
    }

    private static ResolvedFeatureDefinition<Feature, String> descriptor(String name, Feature feature) {
        return new ResolvedFeatureDefinition<>(
                name, name, "1", Feature.class, ignored -> feature,
                Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), FeaturePlacement.ALL_NODES,
                Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of()
        );
    }
}
