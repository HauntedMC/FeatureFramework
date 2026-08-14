package nl.hauntedmc.featureframework.feature;

import nl.hauntedmc.featureframework.config.FeatureConfigHandler;
import nl.hauntedmc.featureframework.lifecycle.FeatureLifecycleResources;

/** Runtime resources required by the shared managed-feature lifecycle. */
public interface FeatureRuntimeContext extends FeatureServiceContext {
    FeatureConfigHandler configHandler();
    FeatureLifecycleResources lifecycle();
    void deactivateServices();
}
