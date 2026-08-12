package nl.hauntedmc.featureframework.host;

import nl.hauntedmc.featureframework.api.feature.FeatureId;
import nl.hauntedmc.featureframework.feature.Feature;
import nl.hauntedmc.featureframework.localization.FeatureLocalization;
import nl.hauntedmc.featureframework.service.FeatureServiceManager;

import java.util.Objects;

/**
 * Default preparation, service activation, and temporary-scope cleanup policy for managed contexts.
 */
public interface ManagedFeatureHostContext extends FeatureHostContext {
    FeatureLocalization localization();
    FeatureServiceManager<FeatureId> services();

    @Override
    default void prepare(Feature feature) {
        Objects.requireNonNull(feature, "feature");
        configHandler().injectDefaults(feature.getDefaultConfig());
        localization().registerDefaultMessages(feature.getDefaultMessages());
        configHandler().reloadConfig();
        localization().reloadLocalization();
    }

    @Override
    default void activateServices() {
        services().activateServices();
    }

    @Override
    default void deactivateServices() {
        services().deactivateServices();
    }

    @Override
    default void cleanup() {
        lifecycle().cleanup();
    }
}
