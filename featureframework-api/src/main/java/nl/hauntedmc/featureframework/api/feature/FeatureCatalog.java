package nl.hauntedmc.featureframework.api.feature;

import java.util.List;
import java.util.Optional;

/** Read-only catalog of all features managed by one host plugin. */
public interface FeatureCatalog {
    Optional<FeatureSnapshot> find(FeatureId id);

    /** Finds a feature by a textual id using the same normalization as {@link FeatureId#of(String)}. */
    default Optional<FeatureSnapshot> findByName(String id) {
        return find(FeatureId.of(id));
    }

    List<FeatureSnapshot> snapshot();
    AutoCloseable subscribe(FeatureCatalogListener listener);
}
