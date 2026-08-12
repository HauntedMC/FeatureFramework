package nl.hauntedmc.featureframework.loader;

import nl.hauntedmc.featureframework.feature.Feature;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class FeatureDescriptorTest {

    @Test
    void normalizesDependenciesAndConstructsTheDeclaredType() {
        Feature feature = mock(Feature.class);
        FeatureDescriptor<Feature, String> descriptor = new FeatureDescriptor<>(
                "demo",
                "Demo",
                "1.0.0",
                Feature.class,
                ignored -> feature,
                Set.of("demo", "Required"),
                Set.of("required", "Optional"),
                Set.of("Plugin")
        );

        assertEquals(Set.of("Required"), descriptor.featureDependencies());
        assertEquals(Set.of("Optional"), descriptor.optionalFeatureDependencies());
        assertSame(feature, descriptor.create("context"));
    }

    @Test
    void rejectsBlankMetadataAndNullConstructionResults() {
        assertThrows(IllegalArgumentException.class, () -> new FeatureDescriptor<>(
                " ", "Demo", "1", Feature.class, ignored -> mock(Feature.class), Set.of(), Set.of()
        ));

        FeatureDescriptor<Feature, String> descriptor = new FeatureDescriptor<>(
                "demo", "Demo", "1", Feature.class, ignored -> null, Set.of(), Set.of()
        );
        assertThrows(IllegalStateException.class, () -> descriptor.create("context"));
    }
}
