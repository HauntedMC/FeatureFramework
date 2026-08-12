package nl.hauntedmc.featureframework.host;

import nl.hauntedmc.featureframework.api.FeatureFrameworkApi;
import nl.hauntedmc.featureframework.api.RuntimeState;
import nl.hauntedmc.featureframework.api.feature.FeatureCatalog;
import nl.hauntedmc.featureframework.api.feature.FeatureId;
import nl.hauntedmc.featureframework.api.feature.FeatureState;
import nl.hauntedmc.featureframework.api.service.CapabilityRegistry;
import nl.hauntedmc.featureframework.config.FeatureConfigurationRoot;
import nl.hauntedmc.featureframework.dependency.DependencyCheckResult;
import nl.hauntedmc.featureframework.feature.LifecycleFeature;
import nl.hauntedmc.featureframework.feature.stateful.FeatureReloadState;
import nl.hauntedmc.featureframework.feature.stateful.SnapshotState;
import nl.hauntedmc.featureframework.loader.FeatureDependencyDiagnostics;
import nl.hauntedmc.featureframework.loader.FeatureDependencyManager;
import nl.hauntedmc.featureframework.loader.FeatureDescriptor;
import nl.hauntedmc.featureframework.loader.FeatureGraphLifecycle;
import nl.hauntedmc.featureframework.loader.FeatureGraphReloadTransaction;
import nl.hauntedmc.featureframework.loader.FeatureKeyResolver;
import nl.hauntedmc.featureframework.loader.FeatureLoadOrderResolver;
import nl.hauntedmc.featureframework.loader.FeatureManifestDiscovery;
import nl.hauntedmc.featureframework.loader.FeatureRegistry;
import nl.hauntedmc.featureframework.loader.FeatureStartupCoordinator;
import nl.hauntedmc.featureframework.operation.FeatureOperationCoordinator;
import nl.hauntedmc.featureframework.operation.disable.FeatureDisableResponse;
import nl.hauntedmc.featureframework.operation.enable.FeatureEnableResponse;
import nl.hauntedmc.featureframework.operation.reload.FeatureReloadResponse;
import nl.hauntedmc.featureframework.operation.reload.FeatureReloadResult;
import nl.hauntedmc.featureframework.operation.reload.FeatureGraphReloadResult;
import nl.hauntedmc.featureframework.operation.reload.FeatureGraphReloader;
import nl.hauntedmc.featureframework.operation.softreload.FeatureSoftReloadResponse;
import nl.hauntedmc.featureframework.runtime.FeatureRuntime;
import nl.hauntedmc.featureframework.service.DefaultCapabilityRegistry;
import nl.hauntedmc.featureframework.toolkit.log.FrameworkLogger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Complete platform-neutral host for a collection of managed features.
 *
 * <p>The host owns discovery, configuration enablement, dependency ordering, preparation, service
 * publication, public catalog transitions, reload cascades, rollback, and reverse-order shutdown.
 * Paper and Velocity adapters only assemble their platform resource scopes and call {@link #start()}
 * and {@link #stop()} from the corresponding plugin lifecycle.</p>
 */
public final class FeatureHost<V, F extends LifecycleFeature<C>, C extends FeatureHostContext>
        implements FeatureFrameworkApi<V>, AutoCloseable {

    private final String hostName;
    private final V version;
    private final String capabilityNamespace;
    private final FeatureRuntime<FeatureId, ? extends DefaultCapabilityRegistry> runtime;
    private final FeatureConfigurationRoot<?> configuration;
    private final FeatureCollection<F, C> collection;
    private final Function<FeatureDescriptor<F, C>, C> contextFactory;
    private final Predicate<String> pluginAvailable;
    private final Runnable afterGraphMutation;
    private final Runnable clearScopes;
    private final Runnable reloadHostResources;
    private final FrameworkLogger logger;
    private final FeatureRegistry<F, FeatureDescriptor<F, C>> registry = new FeatureRegistry<>();
    private final Set<String> preparationFailures = new LinkedHashSet<>();
    private final FeatureDependencyManager dependencyManager;
    private boolean discovered;
    private boolean startAttempted;

    private FeatureHost(Builder<V, F, C> builder) {
        hostName = requireText(builder.hostName, "hostName");
        version = Objects.requireNonNull(builder.version, "version");
        capabilityNamespace = requireText(builder.capabilityNamespace, "capabilityNamespace");
        runtime = Objects.requireNonNull(builder.runtime, "runtime");
        configuration = Objects.requireNonNull(builder.configuration, "configuration");
        collection = Objects.requireNonNull(builder.collection, "collection");
        contextFactory = Objects.requireNonNull(builder.contextFactory, "contextFactory");
        pluginAvailable = Objects.requireNonNull(builder.pluginAvailable, "pluginAvailable");
        afterGraphMutation = Objects.requireNonNull(builder.afterGraphMutation, "afterGraphMutation");
        clearScopes = Objects.requireNonNull(builder.clearScopes, "clearScopes");
        reloadHostResources = builder.reloadHostResources == null
                ? configuration::reloadConfig
                : builder.reloadHostResources;
        logger = Objects.requireNonNull(builder.logger, "logger");
        dependencyManager = new FeatureDependencyManager(
                this::resolveFeatureKey,
                registry::isFeatureLoaded,
                registry::getAvailableFeature,
                this::loadFeature,
                this::missingPluginDependencies,
                registry::getLoadedFeatureNames,
                logger::warn,
                logger::info
        );
    }

    public static <V, F extends LifecycleFeature<C>, C extends FeatureHostContext>
    Builder<V, F, C> builder(
            String hostName,
            V version,
            String capabilityNamespace,
            FeatureRuntime<FeatureId, ? extends DefaultCapabilityRegistry> runtime,
            FeatureConfigurationRoot<?> configuration,
            FeatureCollection<F, C> collection
    ) {
        return new Builder<>(hostName, version, capabilityNamespace, runtime, configuration, collection);
    }

    /** Discovers and starts the configured feature graph. */
    public void start() {
        runtime.lifecycle().runExclusive(this::startLocked);
    }

    private synchronized void startLocked() {
        if (startAttempted) throw new IllegalStateException(hostName + " has already been started");
        startAttempted = true;
        runtime.markStarting();
        try {
            discover();
            prepareFeatureStorage();
            initializeFeatures();
            runtime.markReady();
        } catch (Throwable failure) {
            runtime.markDegraded(failure);
            throwUnchecked(failure);
        }
    }

    private void discover() {
        if (discovered) return;
        FeatureManifestDiscovery.Result<FeatureDescriptor<F, C>, FeatureDefinition<F, C>> result =
                FeatureManifestDiscovery.discover(
                        collection.definitions(), runtime.capabilities().availableTypes(), capabilityNamespace);
        if (!result.conflicts().isEmpty()) {
            throw new IllegalArgumentException("Conflicting feature definitions: " + result.conflicts());
        }

        Map<String, FeatureDefinition<F, C>> definitions = definitionsByName(collection.definitions());
        for (FeatureManifestDiscovery.Discovered<FeatureDescriptor<F, C>, FeatureDefinition<F, C>> item
                : result.discovered()) {
            FeatureDescriptor<F, C> descriptor = item.descriptor();
            FeatureDefinition<F, C> definition = definitions.get(normalize(descriptor.registryName()));
            registry.registerAvailableFeature(descriptor);
            configuration.registerFeature(descriptor.registryName(), definition.enabledByDefault());
            runtime.mutableFeatureCatalog().register(item.publicDescriptor());
            runtime.mutableFeatureCatalog().setConfiguredEnabled(
                    FeatureId.of(descriptor.registryName()), configuration.isFeatureEnabled(descriptor.registryName()));
        }
        pruneMissingFeatureDependencies();
        discovered = true;
        logger.info("[" + hostName + "] Registered features: " + registry.getAvailableFeatures().keySet());
    }

    private void pruneMissingFeatureDependencies() {
        boolean changed;
        do {
            changed = false;
            Set<String> available = new LinkedHashSet<>(registry.getAvailableFeatures().keySet());
            for (FeatureDescriptor<F, C> descriptor : new ArrayList<>(registry.getAvailableFeatures().values())) {
                Set<String> missing = descriptor.featureDependencies().stream()
                        .filter(dependency -> resolveAvailableKey(dependency, available) == null)
                        .collect(Collectors.toCollection(LinkedHashSet::new));
                if (missing.isEmpty()) continue;
                registry.deregisterAvailableFeature(descriptor.registryName());
                runtime.mutableFeatureCatalog().setUnavailableDependencies(
                        FeatureId.of(descriptor.registryName()),
                        missing.stream().map(FeatureId::of).collect(Collectors.toUnmodifiableSet()));
                logger.error("Skipping feature '" + descriptor.registryName()
                        + "' because dependencies are unavailable: " + String.join(", ", missing));
                changed = true;
            }
        } while (changed);
    }

    private void prepareFeatureStorage() {
        for (FeatureDescriptor<F, C> descriptor : registry.getAvailableFeatures().values()) {
            C context = null;
            try {
                context = contextFactory.apply(descriptor);
                F feature = descriptor.create(context);
                context.prepare(feature);
            } catch (Throwable failure) {
                preparationFailures.add(descriptor.registryName());
                runtime.mutableFeatureCatalog().fail(FeatureId.of(descriptor.registryName()), "preparation", failure);
                logger.error("Failed to prepare feature '" + descriptor.registryName() + "'.", failure);
            } finally {
                if (context != null) {
                    try {
                        context.cleanup();
                    } catch (Throwable cleanupFailure) {
                        logger.warn("Failed to clean preparation scope for '"
                                + descriptor.registryName() + "'.", cleanupFailure);
                    }
                }
            }
        }
    }

    private void initializeFeatures() {
        FeatureLoadOrderResolver.Result order = loadOrder(registry.getAvailableFeatures().keySet());
        if (!order.skippedFeatures().isEmpty()) {
            logger.error("Skipping invalid feature graph entries: " + order.skippedFeatures());
        }
        for (String featureName : order.loadOrder()) loadFeature(featureName);
        afterGraphMutation.run();
    }

    public FeatureEnableResponse enableFeature(String featureName) {
        return runtime.lifecycle().callExclusive(() -> FeatureOperationCoordinator.enable(
                featureName,
                this::resolveFeatureKey,
                key -> registry.getAvailableFeature(key) != null,
                registry::isFeatureLoaded,
                this::diagnoseDependencies,
                configuration::isFeatureEnabled,
                this::persistEnabled,
                this::loadFeature,
                afterGraphMutation
        ));
    }

    public FeatureDisableResponse disableFeature(String featureName) {
        return runtime.lifecycle().callExclusive(() -> disableFeatureLocked(featureName));
    }

    private FeatureDisableResponse disableFeatureLocked(String featureName) {
        return FeatureOperationCoordinator.disable(
                featureName,
                this::resolveFeatureKey,
                registry::isFeatureLoaded,
                dependencyManager::getDependentFeatures,
                this::disableFeatureLocked,
                this::stopAndRemove,
                key -> persistEnabled(key, false),
                (key, failure) -> completeDisable(key, failure, "shutdown"),
                afterGraphMutation
        );
    }

    public FeatureSoftReloadResponse softReloadFeature(String featureName) {
        return runtime.lifecycle().callExclusive(() -> FeatureOperationCoordinator.softReload(
                featureName,
                this::resolveFeatureKey,
                registry::isFeatureLoaded,
                key -> {
                    F feature = registry.getLoadedFeature(key);
                    feature.getContext().prepare(feature);
                    return feature.applyConfiguration();
                },
                this::reloadFeatureLocked
        ));
    }

    public FeatureReloadResponse reloadFeature(String featureName) {
        return runtime.lifecycle().callExclusive(() -> reloadFeatureLocked(featureName));
    }

    /** Reloads host configuration and transactionally reconciles every configured feature. */
    public FeatureGraphReloadResult reload() {
        return runtime.lifecycle().callExclusive(this::reloadLocked);
    }

    private FeatureGraphReloadResult reloadLocked() {
        runtime.markReloading();
        FeatureGraphReloadResult result = FeatureGraphReloader.reload(
                reloadHostResources,
                () -> { },
                registry::getLoadedFeatureNames,
                registry::isFeatureLoaded,
                configuration::isFeatureEnabled,
                this::disableFeatureLocked,
                this::reloadFeatureLocked,
                () -> registry.getAvailableFeatures().keySet(),
                this::enableFeature
        );
        if (result.success()) runtime.markReady();
        else runtime.markDegraded();
        return result;
    }

    private FeatureReloadResponse reloadFeatureLocked(String featureName) {
        String key = resolveFeatureKey(featureName);
        if (key == null || !registry.isFeatureLoaded(key)) {
            return new FeatureReloadResponse(FeatureReloadResult.NOT_LOADED, featureName, Set.of());
        }

        FeatureGraphReloadTransaction.Result transaction = FeatureGraphReloadTransaction.execute(
                key,
                () -> buildReloadOrder(key),
                this::captureReloadStates,
                this::stopReloadGraph,
                this::startReloadGraph
        );
        afterGraphMutation.run();
        if (transaction.success()) {
            logger.info("Reloaded feature graph rooted at '" + key + "': " + transaction.reloadOrder());
            return new FeatureReloadResponse(FeatureReloadResult.SUCCESS, key, transaction.reloadedDependents());
        }
        if (transaction.stage() == FeatureGraphReloadTransaction.Stage.PREPARATION) {
            runtime.mutableFeatureCatalog().fail(FeatureId.of(key), "reload-preparation", transaction.failure());
        }
        logger.error("Feature reload failed during " + transaction.stage().name().toLowerCase(Locale.ROOT)
                + " for graph rooted at '" + key + "'.", transaction.failure());
        return new FeatureReloadResponse(FeatureReloadResult.FAILED, key, transaction.reloadedDependents());
    }

    public boolean loadFeature(String featureName) {
        return runtime.lifecycle().callExclusive(() -> loadFeature(featureName, null));
    }

    private boolean loadFeature(String featureName, SnapshotState reloadState) {
        String key = resolveFeatureKey(featureName);
        if (key == null || registry.isFeatureLoaded(key) || preparationFailures.contains(key)) return false;
        FeatureDescriptor<F, C> descriptor = registry.getAvailableFeature(key);
        if (descriptor == null) return false;

        boolean enabled = configuration.isFeatureEnabled(key);
        runtime.mutableFeatureCatalog().setConfiguredEnabled(FeatureId.of(key), enabled);
        if (!enabled || !missingPluginDependencies(key).isEmpty()
                || !dependencyManager.areDependenciesMet(key)) return false;

        return FeatureStartupCoordinator.start(
                reloadState,
                () -> contextFactory.apply(descriptor),
                descriptor::create,
                feature -> feature.getContext().prepare(feature),
                feature -> feature.initialize(),
                feature -> feature.getContext().activateServices(),
                feature -> registry.registerLoadedFeature(key, feature),
                () -> runtime.mutableFeatureCatalog().transition(FeatureId.of(key), FeatureState.STARTING),
                () -> {
                    runtime.mutableFeatureCatalog().setUnavailableDependencies(FeatureId.of(key), Set.of());
                    runtime.mutableFeatureCatalog().transition(FeatureId.of(key), FeatureState.ACTIVE);
                    logger.info("Feature loaded: " + key);
                },
                failure -> {
                    runtime.mutableFeatureCatalog().fail(FeatureId.of(key), "startup", failure);
                    logger.error("Feature '" + key + "' failed to start.", failure);
                },
                LifecycleFeature::cleanup,
                FeatureHostContext::cleanup,
                () -> registry.deregisterLoadedFeature(key)
        );
    }

    /** Stops every feature in reverse dependency/startup order and marks the runtime stopped. */
    public void stop() {
        runtime.lifecycle().runExclusive(this::stopLocked);
    }

    private synchronized void stopLocked() {
        if (runtime.state() == RuntimeState.STOPPED) return;
        runtime.markStopping();
        Throwable failure = unloadAll();
        runtime.markStopped(failure);
        if (failure != null) logger.error(hostName + " shutdown completed with failures.", failure);
    }

    private Throwable unloadAll() {
        Throwable failure = null;
        List<String> loaded = new ArrayList<>(registry.getLoadedFeatureNames());
        Collections.reverse(loaded);
        for (String key : loaded) {
            Throwable featureFailure = stopAndRemove(key);
            completeDisable(key, featureFailure, "shutdown");
            failure = appendFailure(failure, featureFailure);
        }
        clearScopes.run();
        afterGraphMutation.run();
        return failure;
    }

    private Throwable stopAndRemove(String key) {
        F feature = registry.getLoadedFeature(key);
        Throwable failure = null;
        try {
            runtime.mutableFeatureCatalog().transition(FeatureId.of(key), FeatureState.STOPPING);
            if (feature != null) feature.cleanup();
        } catch (Throwable cleanupFailure) {
            failure = cleanupFailure;
        } finally {
            registry.deregisterLoadedFeature(key);
        }
        return failure;
    }

    private void completeDisable(String key, Throwable failure, String phase) {
        if (failure == null) {
            runtime.mutableFeatureCatalog().transition(FeatureId.of(key), FeatureState.DISABLED);
        } else {
            runtime.mutableFeatureCatalog().fail(FeatureId.of(key), phase, failure);
        }
    }

    private List<String> buildReloadOrder(String root) {
        Set<String> affected = FeatureGraphLifecycle.dependentClosure(
                root, dependencyManager::getDependentFeatures, registry::isFeatureLoaded);
        List<String> order = loadOrder(registry.getAvailableFeatures().keySet()).loadOrder().stream()
                .filter(affected::contains)
                .toList();
        if (order.size() != affected.size()) {
            throw new IllegalStateException("Reload graph contains a dependency cycle: " + affected);
        }
        return order;
    }

    private Map<String, Optional<SnapshotState>> captureReloadStates(List<String> reloadOrder) {
        Map<String, Optional<SnapshotState>> states = new LinkedHashMap<>();
        for (String key : reloadOrder) {
            F feature = registry.getLoadedFeature(key);
            if (feature == null) throw new IllegalStateException("Feature disappeared during reload: " + key);
            states.put(key, FeatureReloadState.capture(feature));
        }
        return states;
    }

    private Throwable stopReloadGraph(List<String> order) {
        return FeatureGraphLifecycle.stopReverse(order, key -> {
            Throwable failure = stopAndRemove(key);
            completeDisable(key, failure, "reload-shutdown");
            return failure;
        });
    }

    private boolean startReloadGraph(List<String> order, Map<String, Optional<SnapshotState>> states) {
        return FeatureGraphLifecycle.start(order, key -> loadFeature(key, states.get(key).orElse(null)));
    }

    private FeatureLoadOrderResolver.Result loadOrder(Collection<String> featureNames) {
        return FeatureLoadOrderResolver.resolveLoadOrder(
                featureNames, registry::getAvailableFeature, this::resolveFeatureKey, logger::error);
    }

    private DependencyCheckResult diagnoseDependencies(String featureName) {
        return FeatureDependencyDiagnostics.diagnoseDependenciesRecursively(
                featureName,
                this::resolveFeatureKey,
                registry::getAvailableFeature,
                registry::isFeatureLoaded,
                this::missingPluginDependencies
        );
    }

    public Set<String> missingPluginDependencies(String featureName) {
        String key = resolveFeatureKey(featureName);
        FeatureDescriptor<F, C> descriptor = key == null ? null : registry.getAvailableFeature(key);
        if (descriptor == null) return Set.of();
        return descriptor.pluginDependencies().stream()
                .filter(pluginAvailable.negate())
                .collect(Collectors.toUnmodifiableSet());
    }

    public String resolveFeatureKey(String inputName) {
        return FeatureKeyResolver.resolveFeatureKey(
                inputName,
                registry.getAvailableFeatures(),
                registry.getLoadedFeatureNames(),
                key -> {
                    F feature = registry.getLoadedFeature(key);
                    return feature == null ? null : feature.getFeatureName();
                }
        );
    }

    public FeatureRegistry<F, FeatureDescriptor<F, C>> registry() { return registry; }
    public boolean isLoaded(String featureName) {
        String key = resolveFeatureKey(featureName);
        return key != null && registry.isFeatureLoaded(key);
    }

    private void persistEnabled(String key, boolean enabled) {
        configuration.setFeatureEnabled(key, enabled);
        runtime.mutableFeatureCatalog().setConfiguredEnabled(FeatureId.of(key), enabled);
    }

    @Override public V version() { return version; }
    @Override public RuntimeState state() { return runtime.state(); }
    @Override public CompletionStage<Void> whenReady() { return runtime.whenReady(); }
    @Override public CapabilityRegistry capabilities() { return runtime.capabilities(); }
    @Override public FeatureCatalog features() { return runtime.featureCatalog(); }
    public DefaultCapabilityRegistry mutableCapabilities() { return runtime.capabilities(); }

    @Override
    public void close() {
        stop();
    }

    /** Builder exposing the few host-specific callbacks that platform adapters must supply. */
    public static final class Builder<
            V, F extends LifecycleFeature<C>, C extends FeatureHostContext> {
        private final String hostName;
        private final V version;
        private final String capabilityNamespace;
        private final FeatureRuntime<FeatureId, ? extends DefaultCapabilityRegistry> runtime;
        private final FeatureConfigurationRoot<?> configuration;
        private final FeatureCollection<F, C> collection;
        private Function<FeatureDescriptor<F, C>, C> contextFactory;
        private Predicate<String> pluginAvailable = ignored -> true;
        private Runnable afterGraphMutation = () -> { };
        private Runnable clearScopes = () -> { };
        private Runnable reloadHostResources;
        private FrameworkLogger logger = FrameworkLogger.noop();

        private Builder(
                String hostName,
                V version,
                String capabilityNamespace,
                FeatureRuntime<FeatureId, ? extends DefaultCapabilityRegistry> runtime,
                FeatureConfigurationRoot<?> configuration,
                FeatureCollection<F, C> collection
        ) {
            this.hostName = hostName;
            this.version = version;
            this.capabilityNamespace = capabilityNamespace;
            this.runtime = runtime;
            this.configuration = configuration;
            this.collection = collection;
        }

        public Builder<V, F, C> contextFactory(Function<FeatureDescriptor<F, C>, C> value) {
            contextFactory = Objects.requireNonNull(value, "contextFactory");
            return this;
        }

        public Builder<V, F, C> pluginAvailable(Predicate<String> value) {
            pluginAvailable = Objects.requireNonNull(value, "pluginAvailable");
            return this;
        }

        public Builder<V, F, C> afterGraphMutation(Runnable value) {
            afterGraphMutation = Objects.requireNonNull(value, "afterGraphMutation");
            return this;
        }

        public Builder<V, F, C> clearScopes(Runnable value) {
            clearScopes = Objects.requireNonNull(value, "clearScopes");
            return this;
        }

        public Builder<V, F, C> reloadHostResources(Runnable value) {
            reloadHostResources = Objects.requireNonNull(value, "reloadHostResources");
            return this;
        }

        public Builder<V, F, C> logger(FrameworkLogger value) {
            logger = Objects.requireNonNull(value, "logger");
            return this;
        }

        public FeatureHost<V, F, C> build() {
            return new FeatureHost<>(this);
        }
    }

    private static <F extends LifecycleFeature<C>, C extends FeatureHostContext>
    Map<String, FeatureDefinition<F, C>> definitionsByName(Collection<FeatureDefinition<F, C>> definitions) {
        Map<String, FeatureDefinition<F, C>> result = new LinkedHashMap<>();
        definitions.forEach(definition -> result.put(normalize(definition.featureName()), definition));
        return result;
    }

    private static String resolveAvailableKey(String requested, Set<String> available) {
        return available.stream().filter(requested::equalsIgnoreCase).findFirst().orElse(null);
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT);
    }

    private static String requireText(String value, String field) {
        String clean = Objects.requireNonNull(value, field).trim();
        if (clean.isEmpty()) throw new IllegalArgumentException(field + " must not be blank");
        return clean;
    }

    private static Throwable appendFailure(Throwable current, Throwable additional) {
        if (additional == null) return current;
        if (current == null) return additional;
        current.addSuppressed(additional);
        return current;
    }

    @SuppressWarnings("unchecked")
    private static <E extends Throwable> void throwUnchecked(Throwable failure) throws E {
        throw (E) failure;
    }
}
