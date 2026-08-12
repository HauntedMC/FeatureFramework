package nl.hauntedmc.featureframework.host;

import nl.hauntedmc.featureframework.feature.LifecycleFeature;
import nl.hauntedmc.featureframework.lifecycle.FeatureLifecycleResources;
import nl.hauntedmc.featureframework.toolkit.io.config.ConfigMap;
import nl.hauntedmc.featureframework.toolkit.io.localization.MessageMap;
import nl.hauntedmc.featureframework.toolkit.log.FrameworkLogger;

/** Convenient base for features using a framework-assembled context. */
public abstract class ManagedFeature<
        C extends ManagedFeatureContext<
                ?, ? extends FeatureLifecycleResources, ? extends FrameworkLogger, ?>>
        extends LifecycleFeature<C> {

    protected ManagedFeature(C context) {
        super(context);
    }

    public FrameworkLogger logger() { return getContext().logger(); }

    @Override
    public ConfigMap getDefaultConfig() {
        return new ConfigMap();
    }

    @Override
    public MessageMap getDefaultMessages() {
        return new MessageMap();
    }
}
