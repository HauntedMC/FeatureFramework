package nl.hauntedmc.featureframework.feature;

/** Optional second startup phase invoked after reload state has been restored. */
public interface ActivatableFeature {
    void activate();
}
