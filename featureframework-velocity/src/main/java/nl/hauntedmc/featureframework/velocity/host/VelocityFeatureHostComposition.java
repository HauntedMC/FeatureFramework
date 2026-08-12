package nl.hauntedmc.featureframework.velocity.host;

import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import nl.hauntedmc.featureframework.api.feature.FeatureId;
import nl.hauntedmc.featureframework.config.DefaultFeatureConfiguration;
import nl.hauntedmc.featureframework.config.FeatureConfigHandler;
import nl.hauntedmc.featureframework.host.FeatureCollection;
import nl.hauntedmc.featureframework.host.FeatureHost;
import nl.hauntedmc.featureframework.host.FeatureScopeFactory;
import nl.hauntedmc.featureframework.runtime.FeatureRuntime;
import nl.hauntedmc.featureframework.service.DefaultCapabilityRegistry;
import nl.hauntedmc.featureframework.service.InternalServiceRegistry;
import nl.hauntedmc.featureframework.toolkit.log.FrameworkLogger;
import nl.hauntedmc.featureframework.velocity.lifecycle.VelocityFeatureResources;
import nl.hauntedmc.featureframework.velocity.localization.VelocityLocalization;
import nl.hauntedmc.featureframework.velocity.log.FeatureLogger;

import java.util.Objects;
import java.util.function.Supplier;

/** Framework-owned composition of a product-specific Velocity feature host and its scoped contexts. */
public final class VelocityFeatureHostComposition<
        P,
        V,
        F extends VelocityFeature<P, D>,
        D> implements AutoCloseable {
    private final FeatureScopeFactory<
            F,
            VelocityFeatureContext<P, D>,
            FeatureConfigHandler,
            VelocityLocalization,
            FeatureLogger,
            VelocityFeatureResources<D>> scopes;
    private final FeatureHost<V, F, VelocityFeatureContext<P, D>> host;

    private VelocityFeatureHostComposition(Builder<P, V, F, D> builder) {
        scopes = new FeatureScopeFactory<>(
                builder.configuration::openFeatureConfig,
                builder.localization::openFeature,
                name -> new FeatureLogger(builder.platformLogger, name),
                name -> builder.resources.create(
                        name, builder.runtime.capabilities(), builder.runtime.internalServices()),
                (descriptor, config, localization, logger, resources) -> new VelocityFeatureContext<>(
                        builder.plugin,
                        descriptor,
                        config,
                        localization,
                        resources,
                        logger,
                        builder.runtime.capabilities(),
                        builder.runtime.internalServices(),
                        builder.proxy,
                        builder.dataRegistry)
        );
        host = FeatureHost.builder(
                        builder.hostName,
                        builder.version,
                        builder.capabilityNamespace,
                        builder.runtime,
                        builder.configuration,
                        builder.features)
                .contextFactory(scopes::createContext)
                .pluginAvailable(name -> builder.proxy.getPluginManager().getPlugin(name).isPresent())
                .clearScopes(scopes::clear)
                .reloadHostResources(() -> {
                    builder.configuration.reloadConfig();
                    builder.localization.reloadLocalization();
                    builder.afterHostResourcesReload.run();
                })
                .logger(builder.logger)
                .build();
    }

    public static <P, V, F extends VelocityFeature<P, D>, D>
    Builder<P, V, F, D> builder(
            P plugin,
            ProxyServer proxy,
            ComponentLogger platformLogger,
            V version,
            String capabilityNamespace,
            FeatureRuntime<FeatureId, DefaultCapabilityRegistry> runtime,
            DefaultFeatureConfiguration configuration,
            VelocityLocalization localization,
            ScopedResourcesFactory<D> resources,
            FeatureCollection<F, VelocityFeatureContext<P, D>> features,
            FrameworkLogger logger
    ) {
        return new Builder<>(plugin, proxy, platformLogger, version, capabilityNamespace, runtime,
                configuration, localization, resources, features, logger);
    }

    public FeatureHost<V, F, VelocityFeatureContext<P, D>> host() {
        return host;
    }

    public VelocityLocalization featureLocalization(String featureName) {
        return scopes.localization(featureName);
    }

    public void start() {
        host.start();
    }

    public void stop() {
        host.stop();
    }

    @Override
    public void close() {
        stop();
    }

    /** Creates a fresh framework resource scope for one feature generation. */
    @FunctionalInterface
    public interface ScopedResourcesFactory<D> {
        VelocityFeatureResources<D> create(
                String featureName,
                DefaultCapabilityRegistry capabilities,
                InternalServiceRegistry<FeatureId> internalServices);
    }

    /** Builder for the small amount of product metadata surrounding the standard Velocity host. */
    public static final class Builder<
            P,
            V,
            F extends VelocityFeature<P, D>,
            D> {
        private final P plugin;
        private final ProxyServer proxy;
        private final ComponentLogger platformLogger;
        private final V version;
        private final String capabilityNamespace;
        private final FeatureRuntime<FeatureId, DefaultCapabilityRegistry> runtime;
        private final DefaultFeatureConfiguration configuration;
        private final VelocityLocalization localization;
        private final ScopedResourcesFactory<D> resources;
        private final FeatureCollection<F, VelocityFeatureContext<P, D>> features;
        private final FrameworkLogger logger;
        private String hostName;
        private Runnable afterHostResourcesReload = () -> { };
        private Supplier<?> dataRegistry = () -> {
            throw new IllegalStateException("No DataRegistry supplier was configured for this Velocity host");
        };

        private Builder(
                P plugin,
                ProxyServer proxy,
                ComponentLogger platformLogger,
                V version,
                String capabilityNamespace,
                FeatureRuntime<FeatureId, DefaultCapabilityRegistry> runtime,
                DefaultFeatureConfiguration configuration,
                VelocityLocalization localization,
                ScopedResourcesFactory<D> resources,
                FeatureCollection<F, VelocityFeatureContext<P, D>> features,
                FrameworkLogger logger
        ) {
            this.plugin = Objects.requireNonNull(plugin, "plugin");
            this.proxy = Objects.requireNonNull(proxy, "proxy");
            this.platformLogger = Objects.requireNonNull(platformLogger, "platformLogger");
            this.version = Objects.requireNonNull(version, "version");
            this.capabilityNamespace = requireText(capabilityNamespace, "capabilityNamespace");
            this.runtime = Objects.requireNonNull(runtime, "runtime");
            this.configuration = Objects.requireNonNull(configuration, "configuration");
            this.localization = Objects.requireNonNull(localization, "localization");
            this.resources = Objects.requireNonNull(resources, "resources");
            this.features = Objects.requireNonNull(features, "features");
            this.logger = Objects.requireNonNull(logger, "logger");
            hostName = plugin.getClass().getSimpleName();
        }

        public Builder<P, V, F, D> hostName(String value) {
            hostName = requireText(value, "hostName");
            return this;
        }

        public Builder<P, V, F, D> afterHostResourcesReload(Runnable action) {
            afterHostResourcesReload = Objects.requireNonNull(action, "action");
            return this;
        }

        public Builder<P, V, F, D> dataRegistry(Supplier<?> supplier) {
            dataRegistry = Objects.requireNonNull(supplier, "supplier");
            return this;
        }

        public Builder<P, V, F, D> dataRegistryPlugin(String pluginId) {
            String requiredId = requireText(pluginId, "pluginId");
            return dataRegistry(VelocityDataRegistryPluginDiscovery.supplier(proxy, requiredId));
        }

        public VelocityFeatureHostComposition<P, V, F, D> build() {
            return new VelocityFeatureHostComposition<>(this);
        }
    }

    private static String requireText(String value, String field) {
        String clean = Objects.requireNonNull(value, field).trim();
        if (clean.isEmpty()) throw new IllegalArgumentException(field + " must not be blank");
        return clean;
    }
}
