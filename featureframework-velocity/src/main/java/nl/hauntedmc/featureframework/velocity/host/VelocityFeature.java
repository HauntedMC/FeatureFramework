package nl.hauntedmc.featureframework.velocity.host;

import nl.hauntedmc.featureframework.host.ManagedFeature;
import nl.hauntedmc.featureframework.toolkit.io.config.ConfigService;
import nl.hauntedmc.featureframework.velocity.lifecycle.VelocityFeatureResources;
import nl.hauntedmc.featureframework.velocity.localization.VelocityLocalization;
import nl.hauntedmc.featureframework.velocity.log.FeatureLogger;

/** Base class for a framework-managed feature inside a Velocity plugin. */
public abstract class VelocityFeature<P> extends ManagedFeature<VelocityFeatureContext<P>> {
    protected VelocityFeature(VelocityFeatureContext<P> context) { super(context); }

    public P plugin() { return context().plugin(); }
    @Override public FeatureLogger logger() { return context().logger(); }
    public VelocityFeatureResources resources() { return context().resources(); }
    public VelocityLocalization localization() { return context().localization(); }
    public ConfigService files() { return context().files(); }

    @Override protected void onCleanupStarted() { logger().info("Disabling " + name()); }
}
