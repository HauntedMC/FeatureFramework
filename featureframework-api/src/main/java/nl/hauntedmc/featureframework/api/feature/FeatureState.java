package nl.hauntedmc.featureframework.api.feature;

/** Observable lifecycle state of a built-in feature. */
public enum FeatureState {
    DISABLED,
    SUPPRESSED,
    STARTING,
    ACTIVE,
    STOPPING,
    FAILED
}
