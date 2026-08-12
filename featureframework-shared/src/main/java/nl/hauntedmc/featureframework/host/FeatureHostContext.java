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
    void prepare(Feature feature);
    void activateServices();
    void cleanup();
}
