package nl.hauntedmc.featureframework.host;

import nl.hauntedmc.featureframework.api.feature.FeatureId;
import nl.hauntedmc.featureframework.api.feature.FeatureState;
import nl.hauntedmc.featureframework.config.FeatureConfigurationRoot;
import nl.hauntedmc.featureframework.feature.LifecycleFeature;
import nl.hauntedmc.featureframework.feature.stateful.FeatureReloadState;
import nl.hauntedmc.featureframework.feature.stateful.SnapshotState;
import nl.hauntedmc.featureframework.loader.FeatureDependencyManager;
import nl.hauntedmc.featureframework.loader.FeatureDescriptor;
import nl.hauntedmc.featureframework.loader.FeatureGraphLifecycle;
import nl.hauntedmc.featureframework.loader.FeatureGraphReloadTransaction;
import nl.hauntedmc.featureframework.loader.FeatureRegistry;
import nl.hauntedmc.featureframework.loader.FeatureStartupCoordinator;
import nl.hauntedmc.featureframework.operation.reload.FeatureReloadResponse;
import nl.hauntedmc.featureframework.operation.reload.FeatureReloadResult;
import nl.hauntedmc.featureframework.runtime.FeatureRuntime;
import nl.hauntedmc.featureframework.service.DefaultCapabilityRegistry;
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
    private final Function<FeatureDescriptor<F, C>, C> contextFactory;
    private final FrameworkLogger logger;
    private final FeatureRegistry<F, FeatureDescriptor<F, C>> registry;
    private final Set<String> preparationFailures = new LinkedHashSet<>();
    private final FeatureDependencyManager dependencyManager;

    FeatureInstanceController(
            FeatureInventory<F, C> inventory,
            FeatureRuntime<FeatureId, ? extends DefaultCapabilityRegistry> runtime,
            FeatureConfigurationRoot<?> configuration,
            Function<FeatureDescriptor<F, C>, C> contextFactory,
            FrameworkLogger logger
    ) {
        this.inventory = inventory;
        this.runtime = runtime;
        this.configuration = configuration;
        this.contextFactory = contextFactory;
        this.logger = logger;
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
        if (key == null || registry.isFeatureLoaded(key) || preparationFailures.contains(key)) return false;
        FeatureDescriptor<F, C> descriptor = registry.getAvailableFeature(key);
        if (descriptor == null) return false;

        boolean enabled = configuration.isFeatureEnabled(key);
        runtime.mutableFeatureCatalog().setConfiguredEnabled(FeatureId.of(key), enabled);
        if (!enabled || !inventory.missingPluginDependencies(key).isEmpty()
                || !dependencyManager.areDependenciesMet(key)) return false;

        return FeatureStartupCoordinator.start(
                reloadState,
                () -> contextFactory.apply(descriptor),
                descriptor::create,
                feature -> feature.getContext().prepare(feature),
                LifecycleFeature::initialize,
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

    private List<String> buildReloadOrder(String root) {
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
}
