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

    public final P plugin() { return getContext().plugin(); }
    @Override public final FeatureLogger logger() { return getContext().logger(); }
    public final VelocityFeatureResources<D> resources() { return getContext().resources(); }
    public final VelocityLocalization localization() { return getContext().localization(); }

    /** Compatibility alias for feature implementations migrating from a consumer-owned base. */
    public P getPlugin() { return plugin(); }
    /** Compatibility alias for feature implementations migrating from a consumer-owned base. */
    public FeatureLogger getLogger() { return logger(); }
    /** Compatibility alias for feature implementations migrating from a consumer-owned base. */
    public VelocityFeatureResources<D> getLifecycleManager() { return resources(); }
    /** Compatibility alias for feature implementations migrating from a consumer-owned base. */
    public VelocityLocalization getLocalizationHandler() { return localization(); }

    protected D dataManager() { return resources().getDataManager(); }

    @Override
    protected void onCleanupStarted() {
        logger().info("Disabling " + getFeatureName());
    }
}
