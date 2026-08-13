package nl.hauntedmc.featureframework.paper.host;

import nl.hauntedmc.featureframework.host.ManagedFeature;
import nl.hauntedmc.featureframework.paper.lifecycle.PaperFeatureResources;
import nl.hauntedmc.featureframework.paper.localization.PaperLocalization;
import nl.hauntedmc.featureframework.paper.log.FeatureLogger;
import org.bukkit.plugin.Plugin;

/** Base class for a framework-managed feature inside a Paper plugin. */
public abstract class PaperFeature<P extends Plugin, D> extends ManagedFeature<PaperFeatureContext<P, D>> {
    protected PaperFeature(PaperFeatureContext<P, D> context) {
        super(context);
    }

    public P plugin() { return getContext().plugin(); }
    @Override public FeatureLogger logger() { return getContext().logger(); }
    public PaperFeatureResources<D> resources() { return getContext().resources(); }
    public PaperLocalization localization() { return getContext().localization(); }

    @Override
    protected void onCleanupStarted() {
        logger().info("Disabling " + getFeatureName());
    }
}
