package nl.hauntedmc.featureframework.command;

import nl.hauntedmc.featureframework.feature.Feature;
import nl.hauntedmc.featureframework.loader.FeatureDescriptor;
import nl.hauntedmc.featureframework.loader.FeatureRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FeatureCommandModelTest {
    @Test
    void buildsInfoListsAndCandidatesFromRegistrySnapshots() {
        FeatureRegistry<Feature, FeatureDescriptor<Feature, String>> registry = new FeatureRegistry<>();
        Feature alpha = feature("Alpha", "2", List.of("Core"), List.of("Vault"));
        Feature beta = feature("Beta", "1", List.of(), List.of());
        registry.registerAvailableFeature(descriptor("alpha", "Alpha", "2", alpha));
        registry.registerAvailableFeature(descriptor("beta", "Beta", "1", beta));
        registry.registerLoadedFeature("alpha", alpha);
        FeatureCommandModel<Feature, FeatureDescriptor<Feature, String>> model =
                new FeatureCommandModel<>(registry, value -> "BETA".equalsIgnoreCase(value) ? "beta" : value);

        FeatureCommandModel.FeatureInfo loaded = model.info("ALPHA").orElseThrow();
        FeatureCommandModel.FeatureInfo available = model.info("BETA").orElseThrow();

        assertTrue(loaded.enabled());
        assertEquals(List.of("Core"), loaded.featureDependencies());
        assertFalse(available.enabled());
        assertEquals("Beta", available.name());
        assertEquals(List.of("Alpha"), model.loadedEntries().stream().map(FeatureCommandView.FeatureListEntry::name).toList());
        assertEquals(List.of("beta"), model.enableCandidates("b"));
        assertEquals(2, model.allSuggestions("").size());
    }

    private static Feature feature(String name, String version, List<String> dependencies, List<String> plugins) {
        Feature feature = mock(Feature.class);
        when(feature.getFeatureName()).thenReturn(name);
        when(feature.getFeatureVersion()).thenReturn(version);
        when(feature.getDependencies()).thenReturn(dependencies);
        when(feature.getPluginDependencies()).thenReturn(plugins);
        return feature;
    }

    private static FeatureDescriptor<Feature, String> descriptor(
            String key,
            String name,
            String version,
            Feature feature
    ) {
        return new FeatureDescriptor<>(key, name, version, Feature.class, ignored -> feature, Set.of(), Set.of());
    }
}
