package nl.hauntedmc.featureframework.feature;

import nl.hauntedmc.featureframework.config.ConfigReloadResult;
import nl.hauntedmc.featureframework.service.FeatureServices;

import java.util.List;
import java.util.Objects;

/** Shared metadata and service-resolution behavior for Paper and Velocity feature bases. */
public abstract class AbstractFeature<C extends FeatureServiceContext> implements Feature {
    private final C context;

    protected AbstractFeature(C context) {
        this.context = Objects.requireNonNull(context, "context");
    }

    @Override public String name() { return context.featureName(); }
    @Override public String version() { return context.featureVersion(); }
    @Override public List<String> dependencies() { return context.featureDependencies(); }
    public List<String> optionalDependencies() { return context.optionalFeatureDependencies(); }
    @Override public List<String> pluginDependencies() { return context.pluginDependencies(); }
    public C context() { return context; }

    /** The declaration-aware dependency and publication boundary for this feature. */
    public FeatureServices services() { return context.featureServices(); }

    public ConfigReloadResult applyConfiguration() { return ConfigReloadResult.RECREATE_REQUIRED; }
}
