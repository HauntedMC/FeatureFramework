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

    public final P plugin() { return getContext().plugin(); }
    @Override public final FeatureLogger logger() { return getContext().logger(); }
    public final PaperFeatureResources<D> resources() { return getContext().resources(); }
    public final PaperLocalization localization() { return getContext().localization(); }

    /** Compatibility alias for feature implementations migrating from a consumer-owned base. */
    public P getPlugin() { return plugin(); }
    /** Compatibility alias for feature implementations migrating from a consumer-owned base. */
    public FeatureLogger getLogger() { return logger(); }
    /** Compatibility alias for feature implementations migrating from a consumer-owned base. */
    public PaperFeatureResources<D> getLifecycleManager() { return resources(); }
    /** Compatibility alias for feature implementations migrating from a consumer-owned base. */
    public PaperLocalization getLocalizationHandler() { return localization(); }

    @Override
    protected void onCleanupStarted() {
        logger().info("Disabling " + getFeatureName());
    }
}
