package nl.hauntedmc.featureframework.paper.host;

import nl.hauntedmc.featureframework.integration.dataprovider.FeatureDataManager;
import org.bukkit.plugin.Plugin;

/** Standard Paper base for features using framework-owned DataProvider and DataRegistry integration. */
public abstract class PaperDataProviderFeature<P extends Plugin>
        extends PaperDataRegistryFeature<P, FeatureDataManager> {

    protected PaperDataProviderFeature(PaperFeatureContext<P, FeatureDataManager> context) {
        super(context);
    }
}
