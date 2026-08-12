package nl.hauntedmc.featureframework.api;

/** Lifecycle state of a feature-framework runtime as a whole. */
public enum RuntimeState {
    STARTING,
    READY,
    RELOADING,
    DEGRADED,
    STOPPING,
    STOPPED
}
