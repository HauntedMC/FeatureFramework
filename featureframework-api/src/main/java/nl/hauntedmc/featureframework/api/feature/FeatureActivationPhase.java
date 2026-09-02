package nl.hauntedmc.featureframework.api.feature;

/** Lifecycle phase at which an activation policy is being evaluated. */
public enum FeatureActivationPhase {
    /** The host may need a feature instance only to materialize/validate its managed configuration. */
    PREPARATION,

    /** The host is deciding whether the live feature may be started. */
    ACTIVATION
}
