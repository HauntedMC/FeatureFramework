package nl.hauntedmc.featureframework.api.observation;

/** Stable, low-cardinality terminal outcome for one observed framework operation. */
public enum FeatureFrameworkOperationOutcome {
    SUCCESS,
    NO_CHANGE,
    SKIPPED,
    FAILURE;

    /** Returns whether this outcome represents unsuccessful completion. */
    public boolean isFailure() {
        return this == FAILURE;
    }
}
