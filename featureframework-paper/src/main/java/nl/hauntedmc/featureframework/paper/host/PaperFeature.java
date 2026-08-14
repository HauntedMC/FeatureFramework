package nl.hauntedmc.featureframework.paper.host;

import nl.hauntedmc.featureframework.host.ManagedFeature;
import nl.hauntedmc.featureframework.paper.lifecycle.PaperFeatureResources;
import nl.hauntedmc.featureframework.paper.localization.PaperLocalization;
import nl.hauntedmc.featureframework.paper.log.FeatureLogger;
import nl.hauntedmc.featureframework.toolkit.io.config.ConfigService;
import org.bukkit.plugin.Plugin;

/** Base class for a framework-managed feature inside a Paper plugin. */
public abstract class PaperFeature<P extends Plugin> extends ManagedFeature<PaperFeatureContext<P>> {
    protected PaperFeature(PaperFeatureContext<P> context) { super(context); }

    public P plugin() { return context().plugin(); }
    @Override public FeatureLogger logger() { return context().logger(); }
    public PaperFeatureResources resources() { return context().resources(); }
    public PaperLocalization localization() { return context().localization(); }
    public ConfigService files() { return context().files(); }

    @Override protected void onCleanupStarted() { logger().info("Disabling " + name()); }
}
