package nl.hauntedmc.featureframework.feature;

import nl.hauntedmc.featureframework.api.service.CapabilityRegistry;
import nl.hauntedmc.featureframework.config.ConfigReloadResult;
import nl.hauntedmc.featureframework.service.InternalServiceRegistry;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Shared metadata and service-resolution behavior for Paper and Velocity feature bases. */
public abstract class AbstractFeature<C extends FeatureServiceContext> implements Feature {
    private final C context;
    private final CapabilityRegistry capabilities;
    private final InternalServiceRegistry<?> internalServices;

    protected AbstractFeature(C context) {
        this(context, context.capabilities(), context.internalServices());
    }

    protected AbstractFeature(
            C context,
            CapabilityRegistry capabilities,
            InternalServiceRegistry<?> internalServices
    ) {
        this.context = Objects.requireNonNull(context, "context");
        this.capabilities = capabilities;
        this.internalServices = internalServices;
    }

    @Override public String getFeatureName() { return context.featureName(); }
    @Override public String getFeatureVersion() { return context.featureVersion(); }
    @Override public List<String> getDependencies() { return context.featureDependencies(); }
    public List<String> getOptionalDependencies() { return context.optionalFeatureDependencies(); }
    @Override public List<String> getPluginDependencies() { return context.pluginDependencies(); }
    public C getContext() { return context; }

    public <T> Optional<T> findCapability(Class<T> type) {
        return capabilities == null ? Optional.empty() : capabilities.reference(type).get();
    }

    public <T> T requireCapability(Class<T> type) {
        return findCapability(type).orElseThrow(() -> new IllegalStateException(
                "Required capability is unavailable for " + getFeatureName() + ": " + type.getName()
        ));
    }

    public <T> Optional<T> findInternalService(Class<T> type) {
        return internalServices == null ? Optional.empty() : internalServices.find(type);
    }

    public <T> T requireInternalService(Class<T> type) {
        return findInternalService(type).orElseThrow(() -> new IllegalStateException(
                "Required internal service is unavailable for " + getFeatureName() + ": " + type.getName()
        ));
    }

    public ConfigReloadResult applyConfiguration() { return ConfigReloadResult.RECREATE_REQUIRED; }
}
