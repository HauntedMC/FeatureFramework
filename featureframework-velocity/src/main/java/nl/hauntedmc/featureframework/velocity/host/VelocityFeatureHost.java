package nl.hauntedmc.featureframework.velocity.host;

import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import nl.hauntedmc.featureframework.api.FeatureFrameworkApi;
import nl.hauntedmc.featureframework.api.RuntimeState;
import nl.hauntedmc.featureframework.api.feature.FeatureCatalog;
import nl.hauntedmc.featureframework.api.feature.FeatureId;
import nl.hauntedmc.featureframework.api.service.CapabilityRegistry;
import nl.hauntedmc.featureframework.config.DefaultFeatureConfiguration;
import nl.hauntedmc.featureframework.host.FeatureCollection;
import nl.hauntedmc.featureframework.host.FeatureHost;
import nl.hauntedmc.featureframework.operation.disable.FeatureDisableResponse;
import nl.hauntedmc.featureframework.operation.enable.FeatureEnableResponse;
import nl.hauntedmc.featureframework.operation.reload.FeatureReloadResponse;
import nl.hauntedmc.featureframework.operation.reload.FeatureGraphReloadResult;
import nl.hauntedmc.featureframework.operation.softreload.FeatureSoftReloadResponse;
import nl.hauntedmc.featureframework.runtime.FeatureRuntime;
import nl.hauntedmc.featureframework.service.DefaultCapabilityRegistry;
import nl.hauntedmc.featureframework.toolkit.io.config.ConfigService;
import nl.hauntedmc.featureframework.toolkit.io.localization.Language;
import nl.hauntedmc.featureframework.toolkit.log.FrameworkLogger;
import nl.hauntedmc.featureframework.velocity.lifecycle.VelocityFeatureResourcesFactory;
import nl.hauntedmc.featureframework.velocity.localization.VelocityLocalization;

import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.CompletionStage;

/** Ready-to-use Velocity composition root for one or many managed features. */
public final class VelocityFeatureHost implements FeatureFrameworkApi<String>, AutoCloseable {
    private final FeatureHost<String, VelocityFeature<Object, Void>, VelocityFeatureContext<Object, Void>> delegate;

    private VelocityFeatureHost(Builder builder) {
        Object plugin = Objects.requireNonNull(builder.plugin, "plugin");
        ProxyServer proxy = Objects.requireNonNull(builder.proxy, "proxy");
        ComponentLogger platformLogger = Objects.requireNonNull(builder.logger, "logger");
        Path dataDirectory = Objects.requireNonNull(builder.dataDirectory, "dataDirectory");
        FrameworkLogger logger = FrameworkLogger.from(platformLogger);
        DefaultCapabilityRegistry capabilities = new DefaultCapabilityRegistry(
                builder.apiRoot.getPackageName(), builder.apiRoot.getClassLoader());
        FeatureRuntime<FeatureId, DefaultCapabilityRegistry> runtime =
                new FeatureRuntime<>(builder.hostName, capabilities);
        ConfigService configService = new ConfigService(
                dataDirectory, logger, plugin.getClass().getClassLoader());
        DefaultFeatureConfiguration configuration = new DefaultFeatureConfiguration(configService, logger);
        VelocityLocalization localization = new VelocityLocalization(
                platformLogger, plugin.getClass().getClassLoader(), configService, player -> Language.EN);
        VelocityFeatureResourcesFactory<Void> resources =
                VelocityFeatureResourcesFactory.withoutDataProvider(
                        plugin, proxy, platformLogger, dataDirectory, logger);
        delegate = VelocityFeatureHostComposition.builder(
                        plugin,
                        proxy,
                        platformLogger,
                        builder.version,
                        builder.capabilityNamespace,
                        runtime,
                        configuration,
                        localization,
                        resources::create,
                        builder.features,
                        logger)
                .hostName(builder.hostName)
                .build()
                .host();
    }

    public static Builder builder(
            Object plugin,
            ProxyServer proxy,
            ComponentLogger logger,
            Path dataDirectory,
            Class<?> apiRoot,
            FeatureCollection<VelocityFeature<Object, Void>, VelocityFeatureContext<Object, Void>> features
    ) {
        return new Builder(plugin, proxy, logger, dataDirectory, apiRoot, features);
    }

    public void start() { delegate.start(); }
    public void stop() { delegate.stop(); }
    public boolean isLoaded(String featureName) { return delegate.isLoaded(featureName); }
    public FeatureEnableResponse enableFeature(String featureName) { return delegate.enableFeature(featureName); }
    public FeatureDisableResponse disableFeature(String featureName) { return delegate.disableFeature(featureName); }
    public FeatureReloadResponse reloadFeature(String featureName) { return delegate.reloadFeature(featureName); }
    public FeatureGraphReloadResult reload() { return delegate.reload(); }
    public FeatureSoftReloadResponse softReloadFeature(String featureName) {
        return delegate.softReloadFeature(featureName);
    }
    public FeatureHost<String, VelocityFeature<Object, Void>, VelocityFeatureContext<Object, Void>> managedHost() {
        return delegate;
    }
    @Override public String version() { return delegate.version(); }
    @Override public RuntimeState state() { return delegate.state(); }
    @Override public CompletionStage<Void> whenReady() { return delegate.whenReady(); }
    @Override public CapabilityRegistry capabilities() { return delegate.capabilities(); }
    @Override public FeatureCatalog features() { return delegate.features(); }
    @Override public void close() { stop(); }

    /** Builder for values supplied by a Velocity plugin's annotation/bootstrap. */
    public static final class Builder {
        private final Object plugin;
        private final ProxyServer proxy;
        private final ComponentLogger logger;
        private final Path dataDirectory;
        private final Class<?> apiRoot;
        private final FeatureCollection<VelocityFeature<Object, Void>, VelocityFeatureContext<Object, Void>> features;
        private String hostName;
        private String version = "unspecified";
        private String capabilityNamespace;

        private Builder(
                Object plugin,
                ProxyServer proxy,
                ComponentLogger logger,
                Path dataDirectory,
                Class<?> apiRoot,
                FeatureCollection<VelocityFeature<Object, Void>, VelocityFeatureContext<Object, Void>> features
        ) {
            this.plugin = Objects.requireNonNull(plugin, "plugin");
            this.proxy = Objects.requireNonNull(proxy, "proxy");
            this.logger = Objects.requireNonNull(logger, "logger");
            this.dataDirectory = Objects.requireNonNull(dataDirectory, "dataDirectory");
            this.apiRoot = Objects.requireNonNull(apiRoot, "apiRoot");
            this.features = Objects.requireNonNull(features, "features");
            hostName = plugin.getClass().getSimpleName();
            capabilityNamespace = hostName.toLowerCase(java.util.Locale.ROOT);
        }

        public Builder hostName(String value) { hostName = requireText(value, "hostName"); return this; }
        public Builder version(String value) { version = requireText(value, "version"); return this; }
        public Builder capabilityNamespace(String value) {
            capabilityNamespace = requireText(value, "capabilityNamespace");
            return this;
        }
        public VelocityFeatureHost build() { return new VelocityFeatureHost(this); }
    }

    private static String requireText(String value, String field) {
        String clean = Objects.requireNonNull(value, field).trim();
        if (clean.isEmpty()) throw new IllegalArgumentException(field + " must not be blank");
        return clean;
    }
}
