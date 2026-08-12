package nl.hauntedmc.featureframework.api.feature;

/** Callbacks invoked after a public feature projection changes. Callbacks must be non-blocking. */
@FunctionalInterface
public interface FeatureCatalogListener {
    void stateChanged(FeatureSnapshot snapshot);
}
