package nl.hauntedmc.featureframework.operation.reset;

/** Whether recovery was required and whether it completed. */
public enum FeatureResetRollbackOutcome {
    NOT_REQUIRED,
    SUCCEEDED,
    FAILED
}
