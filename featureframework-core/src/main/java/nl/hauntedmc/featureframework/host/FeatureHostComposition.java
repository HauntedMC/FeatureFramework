package nl.hauntedmc.featureframework.host;

import nl.hauntedmc.featureframework.api.RuntimeState;
import nl.hauntedmc.featureframework.api.feature.FeatureCatalog;
import nl.hauntedmc.featureframework.api.feature.FeatureId;
import nl.hauntedmc.featureframework.api.observation.FeatureFrameworkObserver;
import nl.hauntedmc.featureframework.api.service.CapabilityRegistry;
import nl.hauntedmc.featureframework.config.FeatureConfigurationRoot;
import nl.hauntedmc.featureframework.feature.LifecycleFeature;
import nl.hauntedmc.featureframework.lifecycle.FeatureLifecycleResources;
import nl.hauntedmc.featureframework.localization.FeatureLocalization;
import nl.hauntedmc.featureframework.loader.ResolvedFeatureDefinition;
import nl.hauntedmc.featureframework.operation.disable.FeatureDisableResponse;
import nl.hauntedmc.featureframework.operation.enable.FeatureEnableResponse;
import nl.hauntedmc.featureframework.operation.reload.FeatureGraphReloadResult;
import nl.hauntedmc.featureframework.operation.reload.FeatureReloadResponse;
import nl.hauntedmc.featureframework.operation.reset.FeatureFileResetPreview;
import nl.hauntedmc.featureframework.operation.reset.FeatureFileResetRequest;
import nl.hauntedmc.featureframework.operation.reset.FeatureFileResetResponse;
import nl.hauntedmc.featureframework.operation.softreload.FeatureSoftReloadResponse;
import nl.hauntedmc.featureframework.runtime.FeatureRuntime;
import nl.hauntedmc.featureframework.service.DefaultCapabilityRegistry;
import nl.hauntedmc.featureframework.toolkit.log.FrameworkLogger;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Shared composition engine for platform feature hosts.
 *
 * <p>Paper and Velocity supply only native factories and platform hooks. Scope caching, context
 * creation, host construction, configuration reload wiring and graph ownership live here once.</p>
 */
public final class FeatureHostComposition<
        V,
        F extends LifecycleFeature<C>,
        C extends FeatureHostContext,
        CFG,
        LOC,
        LOG,
        R extends FeatureLifecycleResources> implements AutoCloseable {

    private final FeatureScopeFactory<F, C, CFG, LOC, LOG, R> scopes;
    private final FeatureHost<V, F, C> host;

    public FeatureHostComposition(
            String hostName,
            V version,
            String capabilityNamespace,
            FeatureRuntime<FeatureId, ? extends DefaultCapabilityRegistry> runtime,
            FeatureConfigurationRoot<?> configuration,
            FeatureCollection<F, C> features,
            Function<String, ? extends CFG> configFactory,
            Function<String, ? extends LOC> localizationFactory,
            Function<String, ? extends LOG> loggerFactory,
            Function<ResolvedFeatureDefinition<F, C>, ? extends R> resourcesFactory,
            FeatureScopeFactory.ContextAssembler<F, C, CFG, LOC, LOG, R> contextAssembler,
            Predicate<String> pluginAvailable,
            Runnable afterGraphMutation,
            Runnable reloadLocalization,
            Runnable afterHostResourcesReload,
            FrameworkLogger logger
    ) {
        this(
                hostName,
                version,
                capabilityNamespace,
                runtime,
                configuration,
                features,
                configFactory,
                localizationFactory,
                loggerFactory,
                resourcesFactory,
                contextAssembler,
                pluginAvailable,
                afterGraphMutation,
                reloadLocalization,
                afterHostResourcesReload,
                logger,
                FeatureFrameworkObserver.noop()
        );
    }

    public FeatureHostComposition(
            String hostName,
            V version,
            String capabilityNamespace,
            FeatureRuntime<FeatureId, ? extends DefaultCapabilityRegistry> runtime,
            FeatureConfigurationRoot<?> configuration,
            FeatureCollection<F, C> features,
            Function<String, ? extends CFG> configFactory,
            Function<String, ? extends LOC> localizationFactory,
            Function<String, ? extends LOG> loggerFactory,
            Function<ResolvedFeatureDefinition<F, C>, ? extends R> resourcesFactory,
            FeatureScopeFactory.ContextAssembler<F, C, CFG, LOC, LOG, R> contextAssembler,
            Predicate<String> pluginAvailable,
            Runnable afterGraphMutation,
            Runnable reloadLocalization,
            Runnable afterHostResourcesReload,
            FrameworkLogger logger,
            FeatureFrameworkObserver observer
    ) {
        Objects.requireNonNull(runtime, "runtime");
        Objects.requireNonNull(configuration, "configuration");
        scopes = new FeatureScopeFactory<>(
                configFactory, localizationFactory, loggerFactory, resourcesFactory, contextAssembler);
        Runnable reloadResources = () -> {
            configuration.reloadConfig();
            Objects.requireNonNull(reloadLocalization, "reloadLocalization").run();
            Objects.requireNonNull(afterHostResourcesReload, "afterHostResourcesReload").run();
        };
        host = FeatureHost.builder(hostName, version, capabilityNamespace, runtime, configuration, features)
                .contextFactory(scopes::createContext)
                .pluginAvailable(Objects.requireNonNull(pluginAvailable, "pluginAvailable"))
                .afterGraphMutation(Objects.requireNonNull(afterGraphMutation, "afterGraphMutation"))
                .clearScopes(scopes::clear)
                .reloadHostResources(reloadResources)
                .logger(Objects.requireNonNull(logger, "logger"))
                .observer(Objects.requireNonNull(observer, "observer"))
                .build();
    }

    public LOC localization(String featureName) { return scopes.localization(featureName); }
    public CFG config(String featureName) { return scopes.config(featureName); }
    public LOG logger(String featureName) { return scopes.logger(featureName); }
    public void start() { host.start(); }
    public void stop() { host.stop(); }
    public boolean isLoaded(FeatureId id) { return host.isLoaded(id); }
    public FeatureEnableResponse enable(FeatureId id) { return host.enable(id); }
    public FeatureDisableResponse disable(FeatureId id) { return host.disable(id); }
    public FeatureReloadResponse recreate(FeatureId id) { return host.recreate(id); }
    public FeatureSoftReloadResponse softReload(FeatureId id) { return host.softReload(id); }
    public FeatureGraphReloadResult reloadGraph() { return host.reloadGraph(); }
    public FeatureFileResetPreview previewFileReset(FeatureId id, FeatureFileResetRequest request) {
        return host.previewFileReset(id, request);
    }
    public FeatureFileResetResponse resetFiles(FeatureId id, FeatureFileResetRequest request) {
        return host.resetFiles(id, request);
    }
    public boolean reloadFeatureLocalization(FeatureId id) {
        return host.reloadFeatureLocalization(id, name -> {
            Object value = scopes.localization(name);
            if (!(value instanceof FeatureLocalization featureLocalization)) {
                throw new IllegalStateException("Feature localization does not implement FeatureLocalization");
            }
            featureLocalization.reloadLocalization();
        });
    }
    public Optional<FeatureId> resolve(String name) { return host.resolve(name); }
    public Optional<F> findLoaded(FeatureId id) { return host.findLoaded(id); }
    public List<F> loadedFeatures() { return host.loadedFeatures(); }
    public V version() { return host.version(); }
    public RuntimeState state() { return host.state(); }
    public CompletionStage<Void> whenReady() { return host.whenReady(); }
    public CapabilityRegistry capabilities() { return host.capabilities(); }
    public FeatureCatalog features() { return host.features(); }
    @Override public void close() { stop(); }
}
