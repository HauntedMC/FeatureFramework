package nl.hauntedmc.featureframework.paper.host;

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
import nl.hauntedmc.featureframework.paper.command.brigadier.BrigadierDispatcher;
import nl.hauntedmc.featureframework.paper.lifecycle.PaperFeatureResourcesFactory;
import nl.hauntedmc.featureframework.paper.localization.PaperLocalization;
import nl.hauntedmc.featureframework.runtime.FeatureRuntime;
import nl.hauntedmc.featureframework.service.DefaultCapabilityRegistry;
import nl.hauntedmc.featureframework.toolkit.io.config.ConfigService;
import nl.hauntedmc.featureframework.toolkit.io.localization.Language;
import nl.hauntedmc.featureframework.toolkit.log.FrameworkLogger;
import org.bukkit.plugin.Plugin;

import java.util.Objects;
import java.util.concurrent.CompletionStage;

/** Ready-to-use Paper composition root for one or many managed features. */
public final class PaperFeatureHost implements FeatureFrameworkApi<String>, AutoCloseable {
    private final FeatureHost<String, PaperFeature<Plugin, Void>, PaperFeatureContext<Plugin, Void>> delegate;

    private PaperFeatureHost(Builder builder) {
        Plugin plugin = Objects.requireNonNull(builder.plugin, "plugin");
        FrameworkLogger logger = FrameworkLogger.from(plugin.getLogger());
        DefaultCapabilityRegistry capabilities = new DefaultCapabilityRegistry(
                builder.apiRoot.getPackageName(), builder.apiRoot.getClassLoader());
        FeatureRuntime<FeatureId, DefaultCapabilityRegistry> runtime =
                new FeatureRuntime<>(builder.hostName, capabilities);
        ConfigService configService = new ConfigService(
                plugin.getDataFolder().toPath(), logger, plugin.getClass().getClassLoader());
        DefaultFeatureConfiguration configuration = new DefaultFeatureConfiguration(configService, logger);
        PaperLocalization localization = new PaperLocalization(plugin, configService, player -> Language.EN);
        BrigadierDispatcher dispatcher = new BrigadierDispatcher(plugin, logger);
        dispatcher.resolveDispatcher();
        PaperFeatureResourcesFactory<Void> resources = PaperFeatureResourcesFactory.withoutDataProvider(
                plugin, plugin.getDataFolder().toPath(), dispatcher, () -> true, logger);
        delegate = PaperFeatureHostComposition.builder(
                        plugin,
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
            Plugin plugin,
            Class<?> apiRoot,
            FeatureCollection<PaperFeature<Plugin, Void>, PaperFeatureContext<Plugin, Void>> features
    ) {
        return new Builder(plugin, apiRoot, features);
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
    public FeatureHost<String, PaperFeature<Plugin, Void>, PaperFeatureContext<Plugin, Void>> managedHost() {
        return delegate;
    }
    @Override public String version() { return delegate.version(); }
    @Override public RuntimeState state() { return delegate.state(); }
    @Override public CompletionStage<Void> whenReady() { return delegate.whenReady(); }
    @Override public CapabilityRegistry capabilities() { return delegate.capabilities(); }
    @Override public FeatureCatalog features() { return delegate.features(); }
    @Override public void close() { stop(); }

    /** Builder whose defaults are derived from Paper plugin metadata. */
    public static final class Builder {
        private final Plugin plugin;
        private final Class<?> apiRoot;
        private final FeatureCollection<PaperFeature<Plugin, Void>, PaperFeatureContext<Plugin, Void>> features;
        private String hostName;
        private String version;
        private String capabilityNamespace;

        private Builder(
                Plugin plugin,
                Class<?> apiRoot,
                FeatureCollection<PaperFeature<Plugin, Void>, PaperFeatureContext<Plugin, Void>> features
        ) {
            this.plugin = Objects.requireNonNull(plugin, "plugin");
            this.apiRoot = Objects.requireNonNull(apiRoot, "apiRoot");
            this.features = Objects.requireNonNull(features, "features");
            hostName = plugin.getPluginMeta().getName();
            version = plugin.getPluginMeta().getVersion();
            capabilityNamespace = plugin.getPluginMeta().getName().toLowerCase(java.util.Locale.ROOT);
        }

        public Builder hostName(String value) { hostName = requireText(value, "hostName"); return this; }
        public Builder version(String value) { version = requireText(value, "version"); return this; }
        public Builder capabilityNamespace(String value) {
            capabilityNamespace = requireText(value, "capabilityNamespace");
            return this;
        }
        public PaperFeatureHost build() { return new PaperFeatureHost(this); }
    }

    private static String requireText(String value, String field) {
        String clean = Objects.requireNonNull(value, field).trim();
        if (clean.isEmpty()) throw new IllegalArgumentException(field + " must not be blank");
        return clean;
    }
}
