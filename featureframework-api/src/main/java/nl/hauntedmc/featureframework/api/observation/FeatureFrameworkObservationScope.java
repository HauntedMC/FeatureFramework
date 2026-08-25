package nl.hauntedmc.featureframework.api.observation;

/** Adapter-specific context scope activated while FeatureFramework executes observed work. */
@FunctionalInterface
public interface FeatureFrameworkObservationScope extends AutoCloseable {

    @Override
    void close();

    /** Returns the reusable no-op scope. */
    static FeatureFrameworkObservationScope noop() {
        return NoopFeatureFrameworkObservationScope.INSTANCE;
    }
}

enum NoopFeatureFrameworkObservationScope implements FeatureFrameworkObservationScope {
    INSTANCE;

    @Override
    public void close() {
        // Intentionally empty.
    }
}
