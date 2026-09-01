package nl.hauntedmc.featureframework.command;

import nl.hauntedmc.featureframework.api.feature.FeatureId;
import nl.hauntedmc.featureframework.api.feature.FeatureMetadata;
import nl.hauntedmc.featureframework.api.feature.FeatureScope;
import nl.hauntedmc.featureframework.api.feature.FeatureState;
import nl.hauntedmc.featureframework.service.DefaultFeatureCatalog;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeatureCommandModelTest {
    @Test
    void buildsInfoListsAndCandidatesFromPublicCatalogSnapshots() {
        DefaultFeatureCatalog catalog = new DefaultFeatureCatalog();
        FeatureId alpha = FeatureId.of("alpha");
        FeatureId beta = FeatureId.of("beta");
        catalog.register(metadata(alpha, "Alpha", "2", Set.of(FeatureId.of("Core")), Set.of("Vault")));
        catalog.register(metadata(beta, "Beta", "1", Set.of(), Set.of()));
        catalog.transition(alpha, FeatureState.STARTING);
        catalog.transition(alpha, FeatureState.ACTIVE);
        FeatureCommandModel model = new FeatureCommandModel(catalog);

        FeatureCommandModel.FeatureInfo loaded = model.info(" ALPHA ").orElseThrow();
        FeatureCommandModel.FeatureInfo available = model.info(" Beta ").orElseThrow();

        assertTrue(loaded.enabled());
        assertEquals(FeatureScope.NETWORK, loaded.scope());
        assertEquals(List.of("core"), loaded.featureDependencies());
        assertFalse(available.enabled());
        assertEquals("Beta", available.name());
        assertEquals(List.of("Alpha"), model.loadedEntries().stream().map(FeatureCommandModel.FeatureListEntry::name).toList());
        assertEquals(List.of("beta"), model.enableCandidates("b"));
        assertEquals(2, model.allSuggestions("").size());
    }

    private static FeatureMetadata metadata(
            FeatureId id, String name, String version, Set<FeatureId> dependencies, Set<String> plugins
    ) {
        return new FeatureMetadata(id, name, version, dependencies, plugins, Set.of(), Set.of(), Set.of(),
                id.value().equals("alpha") ? FeatureScope.NETWORK : FeatureScope.NODE);
    }
}
