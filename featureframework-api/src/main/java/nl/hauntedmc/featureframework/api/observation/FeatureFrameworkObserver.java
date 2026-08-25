package nl.hauntedmc.featureframework.api.observation;

/** Vendor-neutral observer for meaningful FeatureFramework lifecycle and runtime operations. */
@FunctionalInterface
public interface FeatureFrameworkObserver {

    /** Starts one observation. Observer failures are isolated by FeatureFramework. */
    FeatureFrameworkObservation start(FeatureFrameworkOperationContext context);

    /** Returns the reusable no-op observer. */
    static FeatureFrameworkObserver noop() {
        return NoopFeatureFrameworkObserver.INSTANCE;
    }
}

enum NoopFeatureFrameworkObserver implements FeatureFrameworkObserver {
    INSTANCE;

    @Override
    public FeatureFrameworkObservation start(FeatureFrameworkOperationContext context) {
        return FeatureFrameworkObservation.noop();
    }
}
