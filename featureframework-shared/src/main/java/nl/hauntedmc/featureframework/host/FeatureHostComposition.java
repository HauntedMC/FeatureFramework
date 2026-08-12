package nl.hauntedmc.featureframework.host;

import nl.hauntedmc.featureframework.api.feature.FeatureId;
import nl.hauntedmc.featureframework.config.FeatureConfigurationRoot;
import nl.hauntedmc.featureframework.feature.LifecycleFeature;
import nl.hauntedmc.featureframework.lifecycle.FeatureLifecycleResources;
import nl.hauntedmc.featureframework.runtime.FeatureRuntime;
import nl.hauntedmc.featureframework.service.DefaultCapabilityRegistry;
import nl.hauntedmc.featureframework.toolkit.log.FrameworkLogger;

import java.util.Objects;
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
            Function<String, ? extends R> resourcesFactory,
            FeatureScopeFactory.ContextAssembler<F, C, CFG, LOC, LOG, R> contextAssembler,
            Predicate<String> pluginAvailable,
            Runnable afterGraphMutation,
            Runnable reloadLocalization,
            Runnable afterHostResourcesReload,
            FrameworkLogger logger
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
                .build();
    }

    public FeatureHost<V, F, C> host() { return host; }
    public LOC localization(String featureName) { return scopes.localization(featureName); }
    public CFG config(String featureName) { return scopes.config(featureName); }
    public LOG logger(String featureName) { return scopes.logger(featureName); }
    public void start() { host.start(); }
    public void stop() { host.stop(); }
    @Override public void close() { stop(); }
}
