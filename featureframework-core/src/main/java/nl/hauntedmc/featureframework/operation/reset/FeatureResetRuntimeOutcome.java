package nl.hauntedmc.featureframework.operation.reset;

/** Runtime disposition after a reset attempt reaches its final state. */
public enum FeatureResetRuntimeOutcome {
    ACTIVE,
    DISABLED,
    INACTIVE,
    RESTORED,
    DEGRADED,
    UNCHANGED
}
