package nl.hauntedmc.featureframework.host;

import nl.hauntedmc.featureframework.api.feature.FeatureId;
import nl.hauntedmc.featureframework.api.feature.FeatureState;
import nl.hauntedmc.featureframework.api.observation.FeatureFrameworkObservationScope;
import nl.hauntedmc.featureframework.api.observation.FeatureFrameworkOperationKind;
import nl.hauntedmc.featureframework.api.observation.FeatureFrameworkOperationOutcome;
import nl.hauntedmc.featureframework.config.FeatureConfigurationRoot;
import nl.hauntedmc.featureframework.config.FeatureStoragePaths;
import nl.hauntedmc.featureframework.feature.LifecycleFeature;
import nl.hauntedmc.featureframework.feature.stateful.FeatureReloadState;
import nl.hauntedmc.featureframework.feature.stateful.SnapshotState;
import nl.hauntedmc.featureframework.loader.FeatureDependencyManager;
import nl.hauntedmc.featureframework.loader.FeatureGraphLifecycle;
import nl.hauntedmc.featureframework.loader.FeatureGraphReloadTransaction;
import nl.hauntedmc.featureframework.loader.FeatureRegistry;
import nl.hauntedmc.featureframework.loader.FeatureStartupCoordinator;
import nl.hauntedmc.featureframework.loader.ResolvedFeatureDefinition;
import nl.hauntedmc.featureframework.operation.reload.FeatureReloadResponse;
import nl.hauntedmc.featureframework.operation.reload.FeatureReloadResult;
import nl.hauntedmc.featureframework.operation.reset.FeatureFileResetRequest;
import nl.hauntedmc.featureframework.runtime.FeatureRuntime;
import nl.hauntedmc.featureframework.service.DefaultCapabilityRegistry;
import nl.hauntedmc.featureframework.toolkit.io.config.ConfigMap;
import nl.hauntedmc.featureframework.toolkit.io.localization.MessageMap;
import nl.hauntedmc.featureframework.toolkit.log.FrameworkLogger;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Owns live feature instances and their dependency-aware startup, reload, and shutdown mechanics.
 *
 * <p>The host remains responsible for public operation serialization and runtime state transitions;
 * this controller only performs mutations while the host lifecycle boundary is already held.</p>
 */
final class FeatureInstanceController<F extends LifecycleFeature<C>, C extends FeatureHostContext> {
    private final FeatureInventory<F, C> inventory;
    private final FeatureRuntime<FeatureId, ? extends DefaultCapabilityRegistry> runtime;
    private final FeatureConfigurationRoot<?> configuration;
    private final Function<ResolvedFeatureDefinition<F, C>, C> contextFactory;
    private final FrameworkLogger logger;
    private final FeatureRegistry<F, ResolvedFeatureDefinition<F, C>> registry;
    private final Set<String> preparationFailures = new LinkedHashSet<>();
    private final Map<String, FeatureDefaults> defaults = new LinkedHashMap<>();
    private final FeatureDependencyManager dependencyManager;
    private final FeatureFrameworkObservations observations;

    FeatureInstanceController(
            FeatureInventory<F, C> inventory,
            FeatureRuntime<FeatureId, ? extends DefaultCapabilityRegistry> runtime,
            FeatureConfigurationRoot<?> configuration,
            Function<ResolvedFeatureDefinition<F, C>, C> contextFactory,
            FrameworkLogger logger,
            FeatureFrameworkObservations observations
    ) {
        this.inventory = inventory;
        this.runtime = runtime;
        this.configuration = configuration;
        this.contextFactory = contextFactory;
        this.logger = logger;
        this.observations = observations;
        registry = inventory.registry();
        dependencyManager = new FeatureDependencyManager(
                inventory::resolveFeatureKey,
                registry::isFeatureLoaded,
                registry::getAvailableFeature,
                this::loadFeature,
                inventory::missingPluginDependencies,
                registry::getLoadedFeatureNames,
                logger::warn,
                logger::info
        );
    }

    void prepareFeatureStorage() {
        for (ResolvedFeatureDefinition<F, C> descriptor : registry.getAvailableFeatures().values()) {
            prepareFeatureStorage(descriptor.registryName());
        }
    }

    boolean prepareFeatureStorage(String featureName) {
        String key = inventory.resolveFeatureKey(featureName);
        ResolvedFeatureDefinition<F, C> descriptor = key == null ? null : registry.getAvailableFeature(key);
        if (descriptor == null) return false;
        C context = null;
        try {
            context = contextFactory.apply(descriptor);
            F feature = descriptor.create(context);
            captureDefaults(key, feature);
            context.prepare(feature);
            preparationFailures.remove(key);
            inventory.clearStorageFailure(key);
            if (!registry.isFeatureLoaded(key)) {
                runtime.mutableFeatureCatalog().transition(FeatureId.of(key), FeatureState.DISABLED);
            }
            return true;
        } catch (Throwable failure) {
            preparationFailures.add(key);
            runtime.mutableFeatureCatalog().fail(FeatureId.of(key), "preparation", failure);
            logger.error("Failed to prepare feature '" + key + "'.", failure);
            return false;
        } finally {
            if (context != null) {
                try {
                    context.cleanup();
                } catch (Throwable cleanupFailure) {
                    logger.warn("Failed to clean preparation scope for '" + key + "'.", cleanupFailure);
                }
            }
        }
    }

    boolean regenerateDefaults(String featureName, FeatureFileResetRequest request) {
        String key = inventory.resolveFeatureKey(featureName);
        FeatureDefaults values = key == null ? null : defaults.get(key);
        if (values == null) return false;
        if (request instanceof FeatureFileResetRequest.Config) {
            configuration.injectFeatureDefaults(key, copyConfig(values.config()));
        } else {
            var target = configuration.files().view(FeatureStoragePaths.messagesPath(key), false);
            target.batch(batch -> values.messages().getMessages().forEach(batch::put));
        }
        return true;
    }

    boolean loadFeature(String featureName) {
        return loadFeature(featureName, null);
    }

    List<String> dependentFeatures(String featureName) {
        return dependencyManager.getDependentFeatures(featureName);
    }

    F loadedFeature(String key) {
        return registry.getLoadedFeature(key);
    }

    Throwable stopAndRemove(String key) {
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

    void completeDisable(String key, Throwable failure, String phase) {
        if (failure == null) {
            runtime.mutableFeatureCatalog().transition(FeatureId.of(key), FeatureState.DISABLED);
        } else {
            runtime.mutableFeatureCatalog().fail(FeatureId.of(key), phase, failure);
        }
    }

    FeatureReloadResponse reloadFeature(String featureName) {
        String key = inventory.resolveFeatureKey(featureName);
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

    private boolean loadFeature(String featureName, SnapshotState reloadState) {
        String key = inventory.resolveFeatureKey(featureName);
        if (key == null) return false;

        FeatureId featureId = FeatureId.of(key);
        FeatureFrameworkObservations.Operation observation = observations.start(
                FeatureFrameworkOperationKind.FEATURE_LOAD,
                featureId
        );
        FeatureFrameworkObservationScope scope = observation.openScope();
        try {
            if (registry.isFeatureLoaded(key) || preparationFailures.contains(key) || inventory.hasStorageFailure(key)) {
                observation.complete(FeatureFrameworkOperationOutcome.SKIPPED, null);
                return false;
            }
            ResolvedFeatureDefinition<F, C> descriptor = registry.getAvailableFeature(key);
            if (descriptor == null) {
                observation.complete(FeatureFrameworkOperationOutcome.SKIPPED, null);
                return false;
            }

            boolean enabled = configuration.isFeatureEnabled(key);
            runtime.mutableFeatureCatalog().setConfiguredEnabled(featureId, enabled);
            if (!enabled || !inventory.missingPluginDependencies(key).isEmpty()
                    || !dependencyManager.areDependenciesMet(key)) {
                observation.complete(FeatureFrameworkOperationOutcome.SKIPPED, null);
                return false;
            }

            Throwable[] startupFailure = observation.isNoop() ? null : new Throwable[1];
            boolean loaded = FeatureStartupCoordinator.start(
                    reloadState,
                    () -> contextFactory.apply(descriptor),
                    descriptor::create,
                    feature -> {
                        captureDefaults(key, feature);
                        feature.context().prepare(feature);
                    },
                    LifecycleFeature::initialize,
                    feature -> feature.context().activateServices(),
                    feature -> registry.registerLoadedFeature(key, feature),
                    () -> runtime.mutableFeatureCatalog().transition(featureId, FeatureState.STARTING),
                    () -> {
                        runtime.mutableFeatureCatalog().setUnavailableDependencies(featureId, Set.of());
                        runtime.mutableFeatureCatalog().transition(featureId, FeatureState.ACTIVE);
                        logger.info("Feature loaded: " + key);
                    },
                    failure -> {
                        if (startupFailure != null) startupFailure[0] = failure;
                        runtime.mutableFeatureCatalog().fail(featureId, "startup", failure);
                        logger.error("Feature '" + key + "' failed to start.", failure);
                    },
                    LifecycleFeature::cleanup,
                    FeatureHostContext::cleanup,
                    () -> registry.deregisterLoadedFeature(key)
            );
            observation.complete(
                    loaded ? FeatureFrameworkOperationOutcome.SUCCESS : FeatureFrameworkOperationOutcome.FAILURE,
                    startupFailure == null ? null : startupFailure[0]
            );
            return loaded;
        } catch (Throwable failure) {
            observation.complete(FeatureFrameworkOperationOutcome.FAILURE, failure);
            return throwUnchecked(failure);
        } finally {
            scope.close();
        }
    }

    List<String> buildReloadOrder(String root) {
        Set<String> affected = FeatureGraphLifecycle.dependentClosure(
                root, dependencyManager::getDependentFeatures, registry::isFeatureLoaded);
        List<String> order = inventory.loadOrder(registry.getAvailableFeatures().keySet()).loadOrder().stream()
                .filter(affected::contains)
                .toList();
        if (order.size() != affected.size()) {
            throw new IllegalStateException("Reload graph contains a dependency cycle: " + affected);
        }
        return order;
    }

    Map<String, Optional<SnapshotState>> captureReloadStates(List<String> reloadOrder) {
        Map<String, Optional<SnapshotState>> states = new LinkedHashMap<>();
        for (String key : reloadOrder) {
            F feature = registry.getLoadedFeature(key);
            if (feature == null) throw new IllegalStateException("Feature disappeared during reload: " + key);
            states.put(key, FeatureReloadState.capture(feature));
        }
        return states;
    }

    Throwable stopReloadGraph(List<String> order) {
        return FeatureGraphLifecycle.stopReverse(order, key -> {
            Throwable failure = stopAndRemove(key);
            completeDisable(key, failure, "reload-shutdown");
            return failure;
        });
    }

    boolean startReloadGraph(List<String> order, Map<String, Optional<SnapshotState>> states) {
        return FeatureGraphLifecycle.start(order, key -> loadFeature(key, states.get(key).orElse(null)));
    }

    private void captureDefaults(String key, F feature) {
        ConfigMap config = copyConfig(feature.defaultConfig());
        MessageMap messages = new MessageMap();
        MessageMap sourceMessages = feature.defaultMessages();
        if (sourceMessages != null) sourceMessages.getMessages().forEach(messages::add);
        defaults.put(key, new FeatureDefaults(config, messages));
    }

    private static ConfigMap copyConfig(ConfigMap source) {
        ConfigMap copy = new ConfigMap();
        if (source != null) source.forEach((key, value) -> copy.put(key, copyValue(value)));
        return copy;
    }

    private static Object copyValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<Object, Object> copy = new LinkedHashMap<>();
            map.forEach((key, entryValue) -> copy.put(key, copyValue(entryValue)));
            return copy;
        }
        if (value instanceof List<?> list) return list.stream().map(FeatureInstanceController::copyValue).toList();
        if (value instanceof Set<?> set) {
            Set<Object> copy = new LinkedHashSet<>();
            set.forEach(entry -> copy.add(copyValue(entry)));
            return copy;
        }
        if (value != null && value.getClass().isArray()) {
            int length = java.lang.reflect.Array.getLength(value);
            Object copy = java.lang.reflect.Array.newInstance(value.getClass().componentType(), length);
            for (int index = 0; index < length; index++) {
                java.lang.reflect.Array.set(copy, index, copyValue(java.lang.reflect.Array.get(value, index)));
            }
            return copy;
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    private static <T, E extends Throwable> T throwUnchecked(Throwable failure) throws E {
        throw (E) failure;
    }

    private record FeatureDefaults(ConfigMap config, MessageMap messages) { }
}
