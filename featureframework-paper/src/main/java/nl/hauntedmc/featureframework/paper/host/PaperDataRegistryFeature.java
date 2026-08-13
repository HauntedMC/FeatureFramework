package nl.hauntedmc.featureframework.paper.host;

import nl.hauntedmc.dataregistry.api.DataRegistryApi;
import nl.hauntedmc.featureframework.paper.integration.dataregistry.PaperDataRegistryIdentityGate;
import org.bukkit.plugin.Plugin;

/** Paper feature base that supplies the framework DataRegistry identity-readiness context. */
public abstract class PaperDataRegistryFeature<P extends Plugin, D> extends PaperFeature<P, D>
        implements PaperDataRegistryIdentityGate.Context {
    protected PaperDataRegistryFeature(PaperFeatureContext<P, D> context) {
        super(context);
    }

    @Override
    public DataRegistryApi dataRegistry() {
        return DataRegistryApi.class.cast(getContext().dataRegistryService());
    }

    @Override
    public void scheduleContinuation(Runnable continuation) {
        resources().getTaskManager().scheduleOneTimeTask(continuation);
    }

    @Override
    public boolean hostAvailable() {
        return plugin().isEnabled();
    }

    @Override
    public void warn(String message) {
        logger().warning(message);
    }
}
