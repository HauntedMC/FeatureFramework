package nl.hauntedmc.featureframework.api.feature;

import java.util.List;
import java.util.Optional;

/** Read-only catalog of all features managed by one host plugin. */
public interface FeatureCatalog {
    Optional<FeatureSnapshot> find(FeatureId id);
    List<FeatureSnapshot> snapshot();
    AutoCloseable subscribe(FeatureCatalogListener listener);
}
