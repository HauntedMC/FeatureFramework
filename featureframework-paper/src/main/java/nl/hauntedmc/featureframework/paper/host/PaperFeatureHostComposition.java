package nl.hauntedmc.featureframework.paper.host;

import nl.hauntedmc.featureframework.api.feature.FeatureId;
import nl.hauntedmc.featureframework.config.DefaultFeatureConfiguration;
import nl.hauntedmc.featureframework.config.FeatureConfigHandler;
import nl.hauntedmc.featureframework.host.FeatureCollection;
import nl.hauntedmc.featureframework.host.FeatureHost;
import nl.hauntedmc.featureframework.host.FeatureScopeFactory;
import nl.hauntedmc.featureframework.paper.command.sync.CommandSync;
import nl.hauntedmc.featureframework.paper.lifecycle.PaperFeatureOperationExecutor;
import nl.hauntedmc.featureframework.paper.lifecycle.PaperFeatureResources;
import nl.hauntedmc.featureframework.paper.localization.PaperLocalization;
import nl.hauntedmc.featureframework.paper.log.FeatureLogger;
import nl.hauntedmc.featureframework.runtime.FeatureRuntime;
import nl.hauntedmc.featureframework.service.DefaultCapabilityRegistry;
import nl.hauntedmc.featureframework.service.InternalServiceRegistry;
import nl.hauntedmc.featureframework.toolkit.log.FrameworkLogger;
import org.bukkit.plugin.Plugin;

import java.util.Objects;
import java.util.function.Supplier;

/** Framework-owned composition of a product-specific Paper feature host and its scoped contexts. */
public final class PaperFeatureHostComposition<
        P extends Plugin,
        V,
        F extends PaperFeature<P, D>,
        D> implements AutoCloseable {
    private final FeatureScopeFactory<
            F,
            PaperFeatureContext<P, D>,
            FeatureConfigHandler,
            PaperLocalization,
            FeatureLogger,
            PaperFeatureResources<D>> scopes;
    private final FeatureHost<V, F, PaperFeatureContext<P, D>> host;

    private PaperFeatureHostComposition(Builder<P, V, F, D> builder) {
        builder.runtime.lifecycle().bindExecutor(new PaperFeatureOperationExecutor(builder.plugin));
        scopes = new FeatureScopeFactory<>(
                builder.configuration::openFeatureConfig,
                builder.localization::openFeature,
                name -> new FeatureLogger(builder.plugin.getLogger(), name),
                name -> builder.resources.create(
                        name, builder.runtime.capabilities(), builder.runtime.internalServices()),
                (descriptor, config, localization, logger, resources) -> new PaperFeatureContext<>(
                        builder.plugin,
                        descriptor,
                        config,
                        localization,
                        resources,
                        logger,
                        builder.runtime.capabilities(),
                        builder.runtime.internalServices(),
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
                .pluginAvailable(name -> builder.plugin.getServer().getPluginManager().isPluginEnabled(name))
                .afterGraphMutation(() -> CommandSync.apply(builder.plugin))
                .clearScopes(scopes::clear)
                .reloadHostResources(() -> {
                    builder.configuration.reloadConfig();
                    builder.localization.reloadLocalization();
                    builder.afterHostResourcesReload.run();
                })
                .logger(builder.logger)
                .build();
    }

    public static <P extends Plugin, V, F extends PaperFeature<P, D>, D>
    Builder<P, V, F, D> builder(
            P plugin,
            V version,
            String capabilityNamespace,
            FeatureRuntime<FeatureId, DefaultCapabilityRegistry> runtime,
            DefaultFeatureConfiguration configuration,
            PaperLocalization localization,
            ScopedResourcesFactory<D> resources,
            FeatureCollection<F, PaperFeatureContext<P, D>> features,
            FrameworkLogger logger
    ) {
        return new Builder<>(plugin, version, capabilityNamespace, runtime, configuration,
                localization, resources, features, logger);
    }

    public FeatureHost<V, F, PaperFeatureContext<P, D>> host() {
        return host;
    }

    public PaperLocalization featureLocalization(String featureName) {
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
        PaperFeatureResources<D> create(
                String featureName,
                DefaultCapabilityRegistry capabilities,
                InternalServiceRegistry<FeatureId> internalServices);
    }

    /** Builder for the small amount of product metadata surrounding the standard Paper host. */
    public static final class Builder<
            P extends Plugin,
            V,
            F extends PaperFeature<P, D>,
            D> {
        private final P plugin;
        private final V version;
        private final String capabilityNamespace;
        private final FeatureRuntime<FeatureId, DefaultCapabilityRegistry> runtime;
        private final DefaultFeatureConfiguration configuration;
        private final PaperLocalization localization;
        private final ScopedResourcesFactory<D> resources;
        private final FeatureCollection<F, PaperFeatureContext<P, D>> features;
        private final FrameworkLogger logger;
        private String hostName;
        private Runnable afterHostResourcesReload = () -> { };
        private Supplier<?> dataRegistry = () -> {
            throw new IllegalStateException("No DataRegistry supplier was configured for this Paper host");
        };

        private Builder(
                P plugin,
                V version,
                String capabilityNamespace,
                FeatureRuntime<FeatureId, DefaultCapabilityRegistry> runtime,
                DefaultFeatureConfiguration configuration,
                PaperLocalization localization,
                ScopedResourcesFactory<D> resources,
                FeatureCollection<F, PaperFeatureContext<P, D>> features,
                FrameworkLogger logger
        ) {
            this.plugin = Objects.requireNonNull(plugin, "plugin");
            this.version = Objects.requireNonNull(version, "version");
            this.capabilityNamespace = requireText(capabilityNamespace, "capabilityNamespace");
            this.runtime = Objects.requireNonNull(runtime, "runtime");
            this.configuration = Objects.requireNonNull(configuration, "configuration");
            this.localization = Objects.requireNonNull(localization, "localization");
            this.resources = Objects.requireNonNull(resources, "resources");
            this.features = Objects.requireNonNull(features, "features");
            this.logger = Objects.requireNonNull(logger, "logger");
            hostName = plugin.getPluginMeta().getName();
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
            dataRegistry = Objects.requireNonNull(supplier, "dataRegistry");
            return this;
        }

        public Builder<P, V, F, D> dataRegistryPlugin(String pluginName) {
            String requiredName = requireText(pluginName, "pluginName");
            return dataRegistry(PaperDataRegistryPluginDiscovery.supplier(plugin, requiredName));
        }

        public PaperFeatureHostComposition<P, V, F, D> build() {
            return new PaperFeatureHostComposition<>(this);
        }
    }

    private static String requireText(String value, String field) {
        String clean = Objects.requireNonNull(value, field).trim();
        if (clean.isEmpty()) throw new IllegalArgumentException(field + " must not be blank");
        return clean;
    }
}
