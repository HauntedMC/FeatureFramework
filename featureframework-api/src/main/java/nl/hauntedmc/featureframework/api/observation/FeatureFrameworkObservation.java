package nl.hauntedmc.featureframework.api.observation;

/** One in-flight FeatureFramework operation returned by a {@link FeatureFrameworkObserver}. */
public interface FeatureFrameworkObservation {

    /**
     * Activates adapter-specific context while FeatureFramework executes work for this observation.
     * Implementations should return a non-null scope that is closed on the same thread.
     */
    default FeatureFrameworkObservationScope openScope() {
        return FeatureFrameworkObservationScope.noop();
    }

    /** Completes the observation with one stable outcome and optional diagnostic failure. */
    void completed(FeatureFrameworkOperationOutcome outcome, Throwable failure);

    /** Returns the reusable no-op observation. */
    static FeatureFrameworkObservation noop() {
        return NoopFeatureFrameworkObservation.INSTANCE;
    }
}

enum NoopFeatureFrameworkObservation implements FeatureFrameworkObservation {
    INSTANCE;

    @Override
    public void completed(FeatureFrameworkOperationOutcome outcome, Throwable failure) {
        // Intentionally empty.
    }
}
