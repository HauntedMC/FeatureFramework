package nl.hauntedmc.featureframework.operation.reset;

/** Stable high-level outcome of a feature file reset attempt. */
public enum FeatureFileResetResult {
    SUCCESS,
    NOT_FOUND,
    HOST_UNAVAILABLE,
    UNSAFE_TARGET,
    QUIESCE_FAILED,
    BACKUP_FAILED,
    REGENERATION_FAILED,
    RESTART_FAILED,
    ROLLBACK_FAILED
}
