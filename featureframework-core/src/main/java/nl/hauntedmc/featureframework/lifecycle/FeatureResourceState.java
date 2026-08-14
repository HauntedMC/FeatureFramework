package nl.hauntedmc.featureframework.lifecycle;

/** State shared by feature-scoped resources while they accept, drain, and release work. */
public enum FeatureResourceState {
    OPEN,
    QUIESCING,
    CLOSED
}
