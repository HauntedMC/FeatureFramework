package nl.hauntedmc.featureframework.velocity.host;

import nl.hauntedmc.featureframework.integration.dataprovider.FeatureDataManager;

/** Standard Velocity base for features using framework-owned DataProvider and DataRegistry integration. */
public abstract class VelocityDataProviderFeature<P>
        extends VelocityDataRegistryFeature<P, FeatureDataManager> {

    protected VelocityDataProviderFeature(VelocityFeatureContext<P, FeatureDataManager> context) {
        super(context);
    }
}
