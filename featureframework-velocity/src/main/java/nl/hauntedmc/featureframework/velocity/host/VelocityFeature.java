package nl.hauntedmc.featureframework.velocity.host;

import nl.hauntedmc.featureframework.host.ManagedFeature;
import nl.hauntedmc.featureframework.velocity.lifecycle.VelocityFeatureResources;
import nl.hauntedmc.featureframework.velocity.localization.VelocityLocalization;
import nl.hauntedmc.featureframework.velocity.log.FeatureLogger;

/** Base class for a framework-managed feature inside a Velocity plugin. */
public abstract class VelocityFeature<P, D> extends ManagedFeature<VelocityFeatureContext<P, D>> {
    protected VelocityFeature(VelocityFeatureContext<P, D> context) {
        super(context);
    }

    public P plugin() { return getContext().plugin(); }
    @Override public FeatureLogger logger() { return getContext().logger(); }
    public VelocityFeatureResources<D> resources() { return getContext().resources(); }
    public VelocityLocalization localization() { return getContext().localization(); }

    protected D dataManager() { return resources().getDataManager(); }

    @Override
    protected void onCleanupStarted() {
        logger().info("Disabling " + getFeatureName());
    }
}
