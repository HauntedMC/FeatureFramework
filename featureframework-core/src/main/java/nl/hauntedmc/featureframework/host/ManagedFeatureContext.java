package nl.hauntedmc.featureframework.host;

import nl.hauntedmc.featureframework.api.feature.FeatureId;
import nl.hauntedmc.featureframework.api.service.CapabilityRegistry;
import nl.hauntedmc.featureframework.config.FeatureConfigHandler;
import nl.hauntedmc.featureframework.lifecycle.FeatureLifecycleResources;
import nl.hauntedmc.featureframework.loader.ResolvedFeatureDefinition;
import nl.hauntedmc.featureframework.localization.FeatureLocalization;
import nl.hauntedmc.featureframework.service.FeatureServiceManager;
import nl.hauntedmc.featureframework.service.InternalServiceRegistry;
import nl.hauntedmc.featureframework.toolkit.log.FrameworkLogger;

import java.util.List;
import java.util.Objects;

/**
 * Standard context assembled by platform hosts for one feature instance.
 *
 * @param <P> platform plugin/bootstrap type
 * @param <R> platform resource-scope type
 * @param <L> platform feature-logger type
 */
public class ManagedFeatureContext<
        P,
        R extends FeatureLifecycleResources,
        L extends FrameworkLogger,
        I extends FeatureLocalization>
        implements ManagedFeatureHostContext {
    private final P plugin;
    private final ResolvedFeatureDefinition<?, ?> descriptor;
    private final FeatureConfigHandler config;
    private final I localization;
    private final R resources;
    private final L logger;
    private final CapabilityRegistry capabilities;
    private final InternalServiceRegistry<FeatureId> internalServices;
    private final FeatureServiceManager<FeatureId> services;

    public ManagedFeatureContext(
            P plugin,
            ResolvedFeatureDefinition<?, ?> descriptor,
            FeatureConfigHandler config,
            I localization,
            R resources,
            L logger,
            CapabilityRegistry capabilities,
            InternalServiceRegistry<FeatureId> internalServices,
            FeatureServiceManager<FeatureId> services
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.descriptor = Objects.requireNonNull(descriptor, "descriptor");
        this.config = Objects.requireNonNull(config, "config");
        this.localization = Objects.requireNonNull(localization, "localization");
        this.resources = Objects.requireNonNull(resources, "resources");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.capabilities = Objects.requireNonNull(capabilities, "capabilities");
        this.internalServices = Objects.requireNonNull(internalServices, "internalServices");
        this.services = Objects.requireNonNull(services, "services");
    }

    public P plugin() { return plugin; }
    @Override public String featureName() { return descriptor.featureName(); }
    @Override public String featureVersion() { return descriptor.featureVersion(); }
    @Override public List<String> featureDependencies() { return List.copyOf(descriptor.featureDependencies()); }
    @Override public List<String> optionalFeatureDependencies() {
        return List.copyOf(descriptor.optionalFeatureDependencies());
    }
    @Override public List<String> pluginDependencies() { return List.copyOf(descriptor.pluginDependencies()); }
    @Override public FeatureConfigHandler configHandler() { return config; }
    @Override public I localization() { return localization; }
    @Override public R lifecycle() { return resources; }
    public R resources() { return resources; }
    public L logger() { return logger; }
    @Override public CapabilityRegistry capabilities() { return capabilities; }
    @Override public InternalServiceRegistry<FeatureId> internalServices() { return internalServices; }
    @Override public FeatureServiceManager<FeatureId> services() { return services; }

}
