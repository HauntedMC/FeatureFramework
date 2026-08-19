package nl.hauntedmc.featureframework.api.feature;

import java.util.List;
import java.util.Optional;

/** Read-only catalog of all features managed by one host plugin. */
public interface FeatureCatalog {
    Optional<FeatureSnapshot> find(FeatureId id);

    /** Finds a feature from external text, returning empty for null or malformed identifiers. */
    default Optional<FeatureSnapshot> findByName(String id) {
        return FeatureId.tryParse(id).flatMap(this::find);
    }

    List<FeatureSnapshot> snapshot();
    AutoCloseable subscribe(FeatureCatalogListener listener);
}
