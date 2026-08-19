package nl.hauntedmc.featureframework.host;

import nl.hauntedmc.featureframework.api.feature.FeatureRole;
import nl.hauntedmc.featureframework.feature.Feature;
import nl.hauntedmc.featureframework.toolkit.io.config.ConfigMap;
import nl.hauntedmc.featureframework.toolkit.io.localization.MessageMap;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeatureCollectionTest {
    @Test
    void builderAcceptsOrderedBatchesAndReportsSize() {
        FeatureDefinition<Feature, Object> first = definition("First");
        FeatureDefinition<Feature, Object> second = definition("Second");

        FeatureCollection<Feature, Object> collection = FeatureCollection.<Feature, Object>builder()
                .features(List.of(first, second))
                .build();

        assertEquals(2, collection.size());
        assertEquals(List.of(first, second), collection.definitions());
    }

    @Test
    void effectiveRolesAreDerivedOnceForImmutableDefinitions() {
        FeatureDefinition<Feature, Object> definition = FeatureDefinition.<Feature, Object>builder(
                        "Capability", "1", DummyFeature.class, ignored -> new DummyFeature("Capability"))
                .providesCapabilities(Runnable.class)
                .build();

        assertTrue(definition.roles().contains(FeatureRole.CAPABILITY_PROVIDER));
        assertSame(definition.roles(), definition.roles());
    }

    private static FeatureDefinition<Feature, Object> definition(String name) {
        return FeatureDefinition.<Feature, Object>builder(
                name, "1", DummyFeature.class, ignored -> new DummyFeature(name)).build();
    }

    private static final class DummyFeature implements Feature {
        private final String name;

        private DummyFeature(String name) {
            this.name = name;
        }

        @Override public String name() { return name; }
        @Override public String version() { return "1"; }
        @Override public List<String> dependencies() { return List.of(); }
        @Override public List<String> pluginDependencies() { return List.of(); }
        @Override public ConfigMap defaultConfig() { return new ConfigMap(); }
        @Override public MessageMap defaultMessages() { return new MessageMap(); }
        @Override public void initialize() { }
        @Override public void disable() { }
    }
}
