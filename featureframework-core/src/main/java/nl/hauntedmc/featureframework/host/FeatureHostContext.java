package nl.hauntedmc.featureframework.host;

import nl.hauntedmc.featureframework.feature.Feature;
import nl.hauntedmc.featureframework.feature.FeatureRuntimeContext;

/**
 * Context operations required by the reusable graph host.
 *
 * <p>Products with specialized contexts can implement this interface directly; using
 * {@link ManagedFeatureContext} is optional.</p>
 */
public interface FeatureHostContext extends FeatureRuntimeContext {
    /**
     * Materializes feature-owned storage during startup preflight without requiring runtime state to be loaded.
     *
     * <p>The default preserves the legacy contract for custom contexts. Managed contexts override this with a
     * storage-only implementation so startup preflight does not duplicate config/localization reload work.</p>
     */
    default void prepareStorage(Feature feature) {
        prepare(feature);
    }

    void prepare(Feature feature);
    void activateServices();
    void cleanup();
}
