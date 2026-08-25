package nl.hauntedmc.featureframework.paper.host;

import nl.hauntedmc.featureframework.api.FeatureFrameworkApi;
import nl.hauntedmc.featureframework.api.RuntimeState;
import nl.hauntedmc.featureframework.api.feature.FeatureCatalog;
import nl.hauntedmc.featureframework.api.feature.FeatureId;
import nl.hauntedmc.featureframework.api.observation.FeatureFrameworkObserver;
import nl.hauntedmc.featureframework.api.service.CapabilityRegistry;
import nl.hauntedmc.featureframework.config.DefaultFeatureConfiguration;
import nl.hauntedmc.featureframework.config.FeatureConfigHandler;
import nl.hauntedmc.featureframework.host.FeatureCollection;
import nl.hauntedmc.featureframework.host.FeatureHostComposition;
import nl.hauntedmc.featureframework.lifecycle.CleanupSequence;
import nl.hauntedmc.featureframework.operation.disable.FeatureDisableResponse;
import nl.hauntedmc.featureframework.operation.enable.FeatureEnableResponse;
import nl.hauntedmc.featureframework.operation.reload.FeatureGraphReloadResult;
import nl.hauntedmc.featureframework.operation.reload.FeatureReloadResponse;
import nl.hauntedmc.featureframework.operation.softreload.FeatureSoftReloadResponse;
import nl.hauntedmc.featureframework.operation.reset.FeatureFileResetPreview;
import nl.hauntedmc.featureframework.operation.reset.FeatureFileResetRequest;
import nl.hauntedmc.featureframework.operation.reset.FeatureFileResetResponse;
import nl.hauntedmc.featureframework.paper.command.brigadier.BrigadierDispatcher;
import nl.hauntedmc.featureframework.paper.command.sync.CommandSync;
import nl.hauntedmc.featureframework.paper.lifecycle.PaperFeatureOperationExecutor;
import nl.hauntedmc.featureframework.paper.lifecycle.PaperFeatureResources;
import nl.hauntedmc.featureframework.paper.lifecycle.PaperFeatureResourcesFactory;
import nl.hauntedmc.featureframework.paper.localization.PaperLocalization;
import nl.hauntedmc.featureframework.paper.localization.PaperMessageDecorator;
import nl.hauntedmc.featureframework.paper.log.FeatureLogger;
import nl.hauntedmc.featureframework.resource.FeatureResourceContributor;
import nl.hauntedmc.featureframework.runtime.FeatureRuntime;
import nl.hauntedmc.featureframework.service.DefaultCapabilityRegistry;
import nl.hauntedmc.featureframework.service.InternalServiceRegistry;
import nl.hauntedmc.featureframework.service.Registration;
import nl.hauntedmc.featureframework.toolkit.io.config.ConfigService;
import nl.hauntedmc.featureframework.toolkit.io.localization.Language;
import nl.hauntedmc.featureframework.toolkit.log.FrameworkLogger;
import nl.hauntedmc.featureframework.theme.Theme;
import nl.hauntedmc.featureframework.theme.ThemeRegistry;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

/** Complete, dependency-clean Paper composition root. */
public final class PaperFeatureHost<P extends Plugin, V> implements FeatureFrameworkApi<V>, AutoCloseable {
    private final FeatureRuntime<FeatureId, DefaultCapabilityRegistry> runtime;
    private final FeatureHostComposition<V, PaperFeature<P>, PaperFeatureContext<P>,
            FeatureConfigHandler, PaperLocalization, FeatureLogger, PaperFeatureResources> composition;
    private final DefaultFeatureConfiguration configuration;
    private final ConfigService files;
    private final PaperLocalization localization;
    private final ThemeRegistry themes;
    private final List<Registration> bootstrapRegistrations;

    private PaperFeatureHost(Builder<P, V> builder) {
        FrameworkLogger frameworkLogger = FrameworkLogger.from(builder.plugin.getLogger());
        DefaultCapabilityRegistry capabilities = new DefaultCapabilityRegistry(
                builder.apiRoot.getPackageName(), builder.apiRoot.getClassLoader());
        runtime = new FeatureRuntime<>(builder.hostName, capabilities);
        runtime.lifecycle().bindExecutor(new PaperFeatureOperationExecutor(builder.plugin));
        files = new ConfigService(builder.plugin.getDataFolder().toPath(), frameworkLogger,
                builder.plugin.getClass().getClassLoader());
        configuration = new DefaultFeatureConfiguration(
                files, frameworkLogger, builder.mismatchPolicy, builder.globalDefaults);
        themes = ThemeRegistry.of(builder.themes);
        localization = new PaperLocalization(
                builder.plugin, files, builder.languageResolver, builder.messageDecorator, themes);
        BrigadierDispatcher dispatcher = new BrigadierDispatcher(builder.plugin, frameworkLogger);
        dispatcher.resolveDispatcher();
        PaperFeatureResourcesFactory resources = new PaperFeatureResourcesFactory(
                builder.plugin,
                builder.plugin.getDataFolder().toPath(),
                dispatcher,
                () -> configuration.getGlobalSetting(builder.overwriteCommandConflictsKey, Boolean.class, true),
                frameworkLogger,
                builder.contributors
        );
        bootstrapRegistrations = registerBootstrap(capabilities, builder.bootstrapCapabilities);
        composition = new FeatureHostComposition<>(
                builder.hostName,
                builder.version,
                builder.capabilityNamespace,
                runtime,
                configuration,
                builder.features,
                configuration::openFeatureConfig,
                localization::openFeature,
                name -> new FeatureLogger(builder.plugin.getLogger(), name),
                definition -> resources.create(definition, capabilities, runtime.internalServices()),
                (definition, config, messages, logger, scope) -> new PaperFeatureContext<>(
                        builder.plugin, definition, config, messages, scope, logger,
                        capabilities, runtime.internalServices(), files),
                name -> builder.plugin.getServer().getPluginManager().isPluginEnabled(name),
                () -> CommandSync.apply(builder.plugin),
                localization::reloadLocalization,
                builder.afterHostResourcesReload,
                frameworkLogger,
                builder.observer
        );
    }

    public static <P extends Plugin, V> Builder<P, V> builder(
            P plugin,
            V version,
            Class<?> apiRoot,
            FeatureCollection<PaperFeature<P>, PaperFeatureContext<P>> features
    ) {
        return new Builder<>(plugin, version, apiRoot, features);
    }

    /** Builds a String-versioned host using the version already declared by the Paper plugin. */
    public static <P extends Plugin> Builder<P, String> builder(
            P plugin,
            Class<?> apiRoot,
            FeatureCollection<PaperFeature<P>, PaperFeatureContext<P>> features
    ) {
        return builder(plugin, plugin.getPluginMeta().getVersion(), apiRoot, features);
    }

    public void start() { composition.start(); }
    public void stop() { composition.stop(); }
    public boolean isLoaded(FeatureId id) { return composition.isLoaded(id); }
    public FeatureEnableResponse enable(FeatureId id) { return composition.enable(id); }
    public FeatureDisableResponse disable(FeatureId id) { return composition.disable(id); }
    public FeatureReloadResponse recreate(FeatureId id) { return composition.recreate(id); }
    public FeatureSoftReloadResponse softReload(FeatureId id) { return composition.softReload(id); }
    public FeatureGraphReloadResult reloadGraph() { return composition.reloadGraph(); }
    public FeatureFileResetPreview previewFileReset(FeatureId id, FeatureFileResetRequest request) {
        return composition.previewFileReset(id, request);
    }
    public FeatureFileResetResponse resetFiles(FeatureId id, FeatureFileResetRequest request) {
        return composition.resetFiles(id, request);
    }
    public boolean reloadFeatureLocalization(FeatureId id) {
        return composition.reloadFeatureLocalization(id);
    }
    public Optional<FeatureId> resolve(String name) { return composition.resolve(name); }
    public Optional<PaperFeature<P>> findLoaded(FeatureId id) { return composition.findLoaded(id); }
    public List<PaperFeature<P>> loadedFeatures() { return composition.loadedFeatures(); }
    public DefaultFeatureConfiguration configuration() { return configuration; }
    public InternalServiceRegistry<FeatureId> internalServices() { return runtime.internalServices(); }
    public ConfigService files() { return files; }
    public PaperLocalization localization() { return localization; }
    public PaperLocalization localization(FeatureId id) { return composition.localization(id.value()); }
    public ThemeRegistry themes() { return themes; }
    @Override public V version() { return composition.version(); }
    @Override public RuntimeState state() { return composition.state(); }
    @Override public CompletionStage<Void> whenReady() { return composition.whenReady(); }
    @Override public CapabilityRegistry capabilities() { return composition.capabilities(); }
    @Override public FeatureCatalog features() { return composition.features(); }

    @Override
    public void close() {
        List<Runnable> cleanup = new ArrayList<>();
        cleanup.add(composition::stop);
        for (int index = bootstrapRegistrations.size() - 1; index >= 0; index--) {
            Registration registration = bootstrapRegistrations.get(index);
            cleanup.add(registration::close);
        }
        CleanupSequence.run(cleanup.toArray(Runnable[]::new));
    }

    private static List<Registration> registerBootstrap(
            DefaultCapabilityRegistry registry,
            List<BootstrapCapability<?>> capabilities
    ) {
        List<Registration> registrations = new ArrayList<>();
        for (BootstrapCapability<?> capability : capabilities) {
            registrations.add(registerBootstrap(registry, capability));
        }
        return List.copyOf(registrations);
    }

    private static <T> Registration registerBootstrap(
            DefaultCapabilityRegistry registry,
            BootstrapCapability<T> capability
    ) {
        return registry.register(FeatureId.of("core"), capability.type(), capability.value());
    }

    private record BootstrapCapability<T>(Class<T> type, T value) { }

    /** Builder for product policy and optional resource contributors. */
    public static final class Builder<P extends Plugin, V> {
        private final P plugin;
        private final V version;
        private final Class<?> apiRoot;
        private final FeatureCollection<PaperFeature<P>, PaperFeatureContext<P>> features;
        private String hostName;
        private String capabilityNamespace;
        private FeatureConfigHandler.TypeMismatchPolicy mismatchPolicy = FeatureConfigHandler.TypeMismatchPolicy.REJECT;
        private Map<String, Object> globalDefaults = Map.of();
        private Function<Player, Language> languageResolver = player -> Language.EN;
        private PaperMessageDecorator messageDecorator = PaperMessageDecorator.identity();
        private String overwriteCommandConflictsKey = "commands.overwrite-conflicts";
        private Runnable afterHostResourcesReload = () -> { };
        private FeatureFrameworkObserver observer = FeatureFrameworkObserver.noop();
        private final List<FeatureResourceContributor<PaperFeatureResources>> contributors = new ArrayList<>();
        private final List<BootstrapCapability<?>> bootstrapCapabilities = new ArrayList<>();
        private final List<Theme> themes = new ArrayList<>();

        private Builder(P plugin, V version, Class<?> apiRoot,
                        FeatureCollection<PaperFeature<P>, PaperFeatureContext<P>> features) {
            this.plugin = Objects.requireNonNull(plugin, "plugin");
            this.version = Objects.requireNonNull(version, "version");
            this.apiRoot = Objects.requireNonNull(apiRoot, "apiRoot");
            this.features = Objects.requireNonNull(features, "features");
            hostName = plugin.getPluginMeta().getName();
            capabilityNamespace = hostName.toLowerCase(Locale.ROOT);
        }

        public Builder<P, V> hostName(String value) { hostName = text(value, "hostName"); return this; }
        public Builder<P, V> capabilityNamespace(String value) {
            capabilityNamespace = text(value, "capabilityNamespace"); return this;
        }
        public Builder<P, V> mismatchPolicy(FeatureConfigHandler.TypeMismatchPolicy value) {
            mismatchPolicy = Objects.requireNonNull(value, "mismatchPolicy"); return this;
        }
        public Builder<P, V> globalDefaults(Map<String, Object> value) {
            globalDefaults = Map.copyOf(value); return this;
        }
        public Builder<P, V> languageResolver(Function<Player, Language> value) {
            languageResolver = Objects.requireNonNull(value, "languageResolver"); return this;
        }
        public Builder<P, V> messageDecorator(PaperMessageDecorator value) {
            messageDecorator = Objects.requireNonNull(value, "messageDecorator"); return this;
        }
        public Builder<P, V> overwriteCommandConflictsKey(String value) {
            overwriteCommandConflictsKey = text(value, "overwriteCommandConflictsKey"); return this;
        }
        public Builder<P, V> afterHostResourcesReload(Runnable value) {
            afterHostResourcesReload = Objects.requireNonNull(value, "afterHostResourcesReload"); return this;
        }
        public Builder<P, V> observer(FeatureFrameworkObserver value) {
            observer = Objects.requireNonNull(value, "observer"); return this;
        }
        public Builder<P, V> contribute(FeatureResourceContributor<PaperFeatureResources> value) {
            contributors.add(Objects.requireNonNull(value, "contributor")); return this;
        }
        public Builder<P, V> contributors(
                Iterable<? extends FeatureResourceContributor<PaperFeatureResources>> values
        ) {
            Objects.requireNonNull(values, "contributors").forEach(this::contribute);
            return this;
        }
        public <T> Builder<P, V> bootstrapCapability(Class<T> type, T value) {
            bootstrapCapabilities.add(new BootstrapCapability<>(type, type.cast(value))); return this;
        }
        public Builder<P, V> theme(Theme value) {
            themes.add(Objects.requireNonNull(value, "theme")); return this;
        }
        public Builder<P, V> themes(Iterable<? extends Theme> values) {
            Objects.requireNonNull(values, "themes").forEach(this::theme); return this;
        }
        public PaperFeatureHost<P, V> build() { return new PaperFeatureHost<>(this); }
    }

    private static String text(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " must not be blank");
        return normalized;
    }
}
