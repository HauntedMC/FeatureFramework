package nl.hauntedmc.featureframework.host;

import nl.hauntedmc.featureframework.api.FeatureFrameworkApi;
import nl.hauntedmc.featureframework.api.RuntimeState;
import nl.hauntedmc.featureframework.api.feature.FeatureCatalog;
import nl.hauntedmc.featureframework.api.feature.FeatureId;
import nl.hauntedmc.featureframework.api.observation.FeatureFrameworkObservationScope;
import nl.hauntedmc.featureframework.api.observation.FeatureFrameworkObserver;
import nl.hauntedmc.featureframework.api.observation.FeatureFrameworkOperationKind;
import nl.hauntedmc.featureframework.api.observation.FeatureFrameworkOperationOutcome;
import nl.hauntedmc.featureframework.api.service.CapabilityRegistry;
import nl.hauntedmc.featureframework.config.FeatureConfigurationRoot;
import nl.hauntedmc.featureframework.feature.LifecycleFeature;
import nl.hauntedmc.featureframework.feature.stateful.SnapshotState;
import nl.hauntedmc.featureframework.loader.FeatureLoadOrderResolver;
import nl.hauntedmc.featureframework.loader.ResolvedFeatureDefinition;
import nl.hauntedmc.featureframework.operation.FeatureOperationCoordinator;
import nl.hauntedmc.featureframework.operation.disable.FeatureDisableResponse;
import nl.hauntedmc.featureframework.operation.disable.FeatureDisableResult;
import nl.hauntedmc.featureframework.operation.enable.FeatureEnableResponse;
import nl.hauntedmc.featureframework.operation.enable.FeatureEnableResult;
import nl.hauntedmc.featureframework.operation.reload.FeatureGraphReloadResult;
import nl.hauntedmc.featureframework.operation.reload.FeatureGraphReloader;
import nl.hauntedmc.featureframework.operation.reload.FeatureReloadResponse;
import nl.hauntedmc.featureframework.operation.reload.FeatureReloadResult;
import nl.hauntedmc.featureframework.operation.reset.FeatureFileResetPreview;
import nl.hauntedmc.featureframework.operation.reset.FeatureFileResetRequest;
import nl.hauntedmc.featureframework.operation.reset.FeatureFileResetResponse;
import nl.hauntedmc.featureframework.operation.reset.FeatureFileResetResult;
import nl.hauntedmc.featureframework.operation.reset.FeatureResetRollbackOutcome;
import nl.hauntedmc.featureframework.operation.reset.FeatureResetRuntimeOutcome;
import nl.hauntedmc.featureframework.operation.softreload.FeatureSoftReloadResponse;
import nl.hauntedmc.featureframework.operation.softreload.FeatureSoftReloadResult;
import nl.hauntedmc.featureframework.runtime.FeatureRuntime;
import nl.hauntedmc.featureframework.service.DefaultCapabilityRegistry;
import nl.hauntedmc.featureframework.toolkit.log.FrameworkLogger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Complete platform-neutral host for a collection of managed features.
 *
 * <p>The host owns operation serialization and runtime state transitions. Discovery and metadata
 * queries are delegated to {@link FeatureInventory}, while live feature instance mechanics are
 * delegated to {@link FeatureInstanceController}. Paper and Velocity adapters only assemble their
 * platform resource scopes and call {@link #start()} and {@link #stop()} from the corresponding
 * plugin lifecycle.</p>
 */
final class FeatureHost<V, F extends LifecycleFeature<C>, C extends FeatureHostContext>
        implements FeatureFrameworkApi<V>, AutoCloseable {

    private final String hostName;
    private final V version;
    private final FeatureRuntime<FeatureId, ? extends DefaultCapabilityRegistry> runtime;
    private final FeatureConfigurationRoot<?> configuration;
    private final Runnable afterGraphMutation;
    private final Runnable clearScopes;
    private final Runnable reloadHostResources;
    private final FrameworkLogger logger;
    private final FeatureInventory<F, C> inventory;
    private final FeatureInstanceController<F, C> controller;
    private final FeatureFileResetStorage resetStorage;
    private final FeatureFrameworkObservations observations;
    private boolean startAttempted;

    private FeatureHost(Builder<V, F, C> builder) {
        hostName = requireText(builder.hostName, "hostName");
        version = Objects.requireNonNull(builder.version, "version");
        String capabilityNamespace = requireText(builder.capabilityNamespace, "capabilityNamespace");
        runtime = Objects.requireNonNull(builder.runtime, "runtime");
        configuration = Objects.requireNonNull(builder.configuration, "configuration");
        FeatureCollection<F, C> collection = Objects.requireNonNull(builder.collection, "collection");
        Function<ResolvedFeatureDefinition<F, C>, C> contextFactory =
                Objects.requireNonNull(builder.contextFactory, "contextFactory");
        Predicate<String> pluginAvailable = Objects.requireNonNull(builder.pluginAvailable, "pluginAvailable");
        afterGraphMutation = Objects.requireNonNull(builder.afterGraphMutation, "afterGraphMutation");
        clearScopes = Objects.requireNonNull(builder.clearScopes, "clearScopes");
        reloadHostResources = builder.reloadHostResources == null
                ? configuration::reloadConfig
                : builder.reloadHostResources;
        logger = Objects.requireNonNull(builder.logger, "logger");
        observations = new FeatureFrameworkObservations(builder.observer);
        resetStorage = new FeatureFileResetStorage(configuration.files(), logger);
        inventory = new FeatureInventory<>(
                capabilityNamespace, runtime, configuration, collection, pluginAvailable, logger);
        controller = new FeatureInstanceController<>(
                inventory, runtime, configuration, contextFactory, logger, observations);
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
        observations.observe(
                FeatureFrameworkOperationKind.HOST_START,
                () -> {
                    startLockedUnobserved();
                    return null;
                },
                ignored -> FeatureFrameworkOperationOutcome.SUCCESS,
                ignored -> null
        );
    }

    private void startLockedUnobserved() {
        if (startAttempted) throw new IllegalStateException(hostName + " has already been started");
        startAttempted = true;
        runtime.markStarting();
        try {
            resetStorage.recoverIncompleteTransactions();
            inventory.discover(hostName);
            controller.prepareFeatureStorage();
            initializeFeatures();
            runtime.markReady();
        } catch (Throwable failure) {
            runtime.markDegraded(failure);
            throwUnchecked(failure);
        }
    }

    private void initializeFeatures() {
        FeatureLoadOrderResolver.Result order = inventory.loadOrder(inventory.registry().getAvailableFeatures().keySet());
        if (!order.skippedFeatures().isEmpty()) {
            logger.error("Skipping invalid feature graph entries: " + order.skippedFeatures());
        }
        for (String featureName : order.loadOrder()) controller.loadFeature(featureName);
        afterGraphMutation.run();
    }

    public FeatureEnableResponse enable(FeatureId id) {
        FeatureId featureId = Objects.requireNonNull(id, "id");
        return runtime.lifecycle().callExclusive(() -> observations.observe(
                FeatureFrameworkOperationKind.FEATURE_ENABLE,
                featureId,
                () -> enableLockedId(featureId.value()),
                FeatureHost::enableOutcome,
                ignored -> null
        ));
    }

    private FeatureEnableResponse enableLockedId(String featureName) {
        return FeatureOperationCoordinator.enable(
                featureName,
                inventory::resolveFeatureKey,
                key -> inventory.registry().getAvailableFeature(key) != null,
                inventory.registry()::isFeatureLoaded,
                inventory::diagnoseDependencies,
                configuration::isFeatureEnabled,
                this::persistEnabled,
                controller::loadFeature,
                afterGraphMutation
        );
    }

    public FeatureDisableResponse disable(FeatureId id) {
        FeatureId featureId = Objects.requireNonNull(id, "id");
        return runtime.lifecycle().callExclusive(() -> observations.observe(
                FeatureFrameworkOperationKind.FEATURE_DISABLE,
                featureId,
                () -> disableFeatureLocked(featureId.value()),
                FeatureHost::disableOutcome,
                ignored -> null
        ));
    }

    private FeatureDisableResponse disableFeatureLocked(String featureName) {
        return FeatureOperationCoordinator.disable(
                featureName,
                inventory::resolveFeatureKey,
                inventory.registry()::isFeatureLoaded,
                controller::dependentFeatures,
                this::disableFeatureLocked,
                controller::stopAndRemove,
                key -> persistEnabled(key, false),
                (key, failure) -> controller.completeDisable(key, failure, "shutdown"),
                afterGraphMutation
        );
    }

    public FeatureSoftReloadResponse softReload(FeatureId id) {
        FeatureId featureId = Objects.requireNonNull(id, "id");
        return runtime.lifecycle().callExclusive(() -> observations.observe(
                FeatureFrameworkOperationKind.FEATURE_SOFT_RELOAD,
                featureId,
                () -> FeatureOperationCoordinator.softReload(
                        featureId.value(),
                        inventory::resolveFeatureKey,
                        inventory.registry()::isFeatureLoaded,
                        key -> {
                            F feature = controller.loadedFeature(key);
                            feature.context().prepare(feature);
                            return feature.applyConfiguration();
                        },
                        this::recreateLocked
                ),
                FeatureHost::softReloadOutcome,
                ignored -> null
        ));
    }

    public FeatureReloadResponse recreate(FeatureId id) {
        FeatureId featureId = Objects.requireNonNull(id, "id");
        return runtime.lifecycle().callExclusive(() -> observations.observe(
                FeatureFrameworkOperationKind.FEATURE_RECREATE,
                featureId,
                () -> recreateLocked(featureId.value()),
                FeatureHost::reloadOutcome,
                ignored -> null
        ));
    }

    /** Reloads host configuration and transactionally reconciles every configured feature. */
    public FeatureGraphReloadResult reloadGraph() {
        return runtime.lifecycle().callExclusive(() -> observations.observe(
                FeatureFrameworkOperationKind.GRAPH_RELOAD,
                this::reloadLocked,
                result -> result.success()
                        ? FeatureFrameworkOperationOutcome.SUCCESS
                        : FeatureFrameworkOperationOutcome.FAILURE,
                result -> result.failure().orElse(null)
        ));
    }

    public FeatureFileResetPreview previewFileReset(FeatureId id, FeatureFileResetRequest request) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(request, "request");
        return runtime.lifecycle().callExclusive(() -> previewFileResetLocked(id.value(), request));
    }

    private FeatureFileResetPreview previewFileResetLocked(String requested, FeatureFileResetRequest request) {
        String key = inventory.resolveFeatureKey(requested);
        if (key == null) {
            return new FeatureFileResetPreview(false, "", request, List.of(), false, false, Set.of(),
                    "Feature not found");
        }
        try {
            boolean loaded = inventory.registry().isFeatureLoaded(key);
            Set<String> dependents = loaded
                    ? new java.util.LinkedHashSet<>(controller.buildReloadOrder(key))
                    : Set.of();
            if (loaded) dependents.remove(key);
            boolean enabled = runtime.mutableFeatureCatalog().find(FeatureId.of(key))
                    .map(snapshot -> snapshot.configuredEnabled()).orElse(inventory.enabledDefault(key));
            return new FeatureFileResetPreview(true, key, request, resetStorage.targets(key, request),
                    enabled, loaded, dependents, "");
        } catch (Throwable failure) {
            return new FeatureFileResetPreview(false, key, request, List.of(), false, false, Set.of(),
                    failure.getMessage());
        }
    }

    public FeatureFileResetResponse resetFiles(FeatureId id, FeatureFileResetRequest request) {
        FeatureId featureId = Objects.requireNonNull(id, "id");
        Objects.requireNonNull(request, "request");
        return runtime.lifecycle().callExclusive(() -> observations.observe(
                FeatureFrameworkOperationKind.FILE_RESET,
                featureId,
                () -> resetFilesLocked(featureId.value(), request),
                FeatureHost::resetOutcome,
                response -> response.failure().orElse(null)
        ));
    }

    boolean reloadFeatureLocalization(FeatureId id, Consumer<String> reload) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(reload, "reload");
        return runtime.lifecycle().callExclusive(() -> {
            String key = inventory.resolveFeatureKey(id.value());
            if (key == null) return false;
            reload.accept(key);
            return true;
        });
    }

    private FeatureFileResetResponse resetFilesLocked(String requested, FeatureFileResetRequest request) {
        if (runtime.state() != RuntimeState.READY && runtime.state() != RuntimeState.DEGRADED) {
            return resetResponse(FeatureFileResetResult.HOST_UNAVAILABLE, requested, request, false,
                    FeatureResetRuntimeOutcome.UNCHANGED, FeatureResetRollbackOutcome.NOT_REQUIRED,
                    Set.of(), List.of(), null, null);
        }
        String key = inventory.resolveFeatureKey(requested);
        if (key == null) {
            return resetResponse(FeatureFileResetResult.NOT_FOUND, requested, request, false,
                    FeatureResetRuntimeOutcome.UNCHANGED, FeatureResetRollbackOutcome.NOT_REQUIRED,
                    Set.of(), List.of(), null, null);
        }

        boolean wasLoaded = inventory.registry().isFeatureLoaded(key);
        boolean configuredEnabled = runtime.mutableFeatureCatalog().find(FeatureId.of(key))
                .map(snapshot -> snapshot.configuredEnabled()).orElse(inventory.enabledDefault(key));
        List<String> order = List.of();
        Map<String, Optional<SnapshotState>> states = Map.of();
        Set<String> dependents = Set.of();
        if (wasLoaded) {
            try {
                order = controller.buildReloadOrder(key);
                states = controller.captureReloadStates(order);
                java.util.LinkedHashSet<String> values = new java.util.LinkedHashSet<>(order);
                values.remove(key);
                dependents = Set.copyOf(values);
            } catch (Throwable failure) {
                return resetResponse(FeatureFileResetResult.QUIESCE_FAILED, key, request, false,
                        FeatureResetRuntimeOutcome.UNCHANGED, FeatureResetRollbackOutcome.NOT_REQUIRED,
                        dependents, List.of(), null, failure);
            }
            Throwable stopFailure = controller.stopReloadGraph(order);
            if (stopFailure != null) {
                boolean restored = controller.startReloadGraph(order, states);
                afterGraphMutation.run();
                return resetResponse(restored ? FeatureFileResetResult.QUIESCE_FAILED
                                : FeatureFileResetResult.ROLLBACK_FAILED,
                        key, request, false,
                        restored ? FeatureResetRuntimeOutcome.RESTORED : FeatureResetRuntimeOutcome.DEGRADED,
                        restored ? FeatureResetRollbackOutcome.SUCCEEDED : FeatureResetRollbackOutcome.FAILED,
                        dependents, List.of(), null, stopFailure);
            }
        }

        FeatureFileResetStorage.Backup backup;
        try {
            backup = resetStorage.begin(key, request);
        } catch (FeatureFileResetStorage.UnsafeTargetException unsafe) {
            restoreGraphAfterPreMutationFailure(wasLoaded, order, states);
            return resetResponse(FeatureFileResetResult.UNSAFE_TARGET, key, request, false,
                    wasLoaded ? FeatureResetRuntimeOutcome.RESTORED : FeatureResetRuntimeOutcome.UNCHANGED,
                    wasLoaded ? FeatureResetRollbackOutcome.SUCCEEDED : FeatureResetRollbackOutcome.NOT_REQUIRED,
                    dependents, List.of(), null, unsafe);
        } catch (Throwable failure) {
            boolean restored = restoreGraphAfterPreMutationFailure(wasLoaded, order, states);
            return resetResponse(restored ? FeatureFileResetResult.BACKUP_FAILED
                            : FeatureFileResetResult.ROLLBACK_FAILED,
                    key, request, false,
                    restored ? (wasLoaded ? FeatureResetRuntimeOutcome.RESTORED : FeatureResetRuntimeOutcome.UNCHANGED)
                            : FeatureResetRuntimeOutcome.DEGRADED,
                    wasLoaded ? (restored ? FeatureResetRollbackOutcome.SUCCEEDED
                            : FeatureResetRollbackOutcome.FAILED) : FeatureResetRollbackOutcome.NOT_REQUIRED,
                    dependents, List.of(), null, failure);
        }

        List<String> deletedOverrides;
        boolean fullyPrepared;
        try {
            deletedOverrides = resetStorage.stage(backup, request);
            boolean stagedMalformedPrerequisites = resetStorage.stageMalformedPrerequisites(backup);
            boolean regeneratedFromSnapshot = controller.regenerateDefaults(key, request);
            fullyPrepared = controller.prepareFeatureStorage(key);
            if (!regeneratedFromSnapshot && !fullyPrepared) {
                throw new IllegalStateException("Feature defaults could not be regenerated");
            }
            if (stagedMalformedPrerequisites) {
                resetStorage.restorePrerequisites(backup);
                fullyPrepared = controller.prepareFeatureStorage(key);
            }
            if (wasLoaded && !fullyPrepared) {
                throw new IllegalStateException("Regenerated files could not prepare the active feature");
            }
            if (request instanceof FeatureFileResetRequest.Config) persistEnabled(key, configuredEnabled);
        } catch (Throwable failure) {
            return rollbackReset(key, request, backup, wasLoaded, order, states, dependents,
                    List.of(), FeatureFileResetResult.REGENERATION_FAILED, failure);
        }

        FeatureResetRuntimeOutcome runtimeOutcome;
        if (wasLoaded) {
            if (!controller.startReloadGraph(order, states)) {
                return rollbackReset(key, request, backup, true, order, states, dependents,
                        deletedOverrides, FeatureFileResetResult.RESTART_FAILED,
                        new IllegalStateException("Replacement feature graph did not start"));
            }
            runtimeOutcome = FeatureResetRuntimeOutcome.ACTIVE;
        } else if (!fullyPrepared) {
            runtimeOutcome = FeatureResetRuntimeOutcome.INACTIVE;
        } else if (!configuredEnabled) {
            runtimeOutcome = FeatureResetRuntimeOutcome.DISABLED;
        } else {
            runtimeOutcome = controller.loadFeature(key)
                    ? FeatureResetRuntimeOutcome.ACTIVE : FeatureResetRuntimeOutcome.INACTIVE;
        }

        try {
            resetStorage.commit(backup);
        } catch (Throwable failure) {
            return rollbackReset(key, request, backup, wasLoaded, order, states, dependents,
                    deletedOverrides, FeatureFileResetResult.BACKUP_FAILED, failure);
        }
        afterGraphMutation.run();
        logger.info("Reset feature files for '" + key + "' using backup '" + backup.id() + "'.");
        return resetResponse(FeatureFileResetResult.SUCCESS, key, request, true, runtimeOutcome,
                FeatureResetRollbackOutcome.NOT_REQUIRED, dependents, deletedOverrides, backup.id(), null);
    }

    private FeatureFileResetResponse rollbackReset(
            String key,
            FeatureFileResetRequest request,
            FeatureFileResetStorage.Backup backup,
            boolean wasLoaded,
            List<String> order,
            Map<String, Optional<SnapshotState>> states,
            Set<String> dependents,
            List<String> deletedOverrides,
            FeatureFileResetResult originalResult,
            Throwable failure
    ) {
        if (wasLoaded) {
            controller.stopReloadGraph(order);
        } else if (inventory.registry().isFeatureLoaded(key)) {
            Throwable cleanupFailure = controller.stopAndRemove(key);
            controller.completeDisable(key, cleanupFailure, "reset-rollback-shutdown");
            if (cleanupFailure != null) failure.addSuppressed(cleanupFailure);
        }
        boolean filesRestored;
        try {
            resetStorage.restore(backup);
            filesRestored = true;
            controller.prepareFeatureStorage(key);
            resetStorage.markRolledBack(backup);
        } catch (Throwable restoreFailure) {
            failure.addSuppressed(restoreFailure);
            filesRestored = false;
        }
        boolean graphRestored = !wasLoaded || (filesRestored && controller.startReloadGraph(order, states));
        afterGraphMutation.run();
        boolean restored = filesRestored && graphRestored;
        return resetResponse(restored ? originalResult : FeatureFileResetResult.ROLLBACK_FAILED,
                key, request, false,
                restored ? (wasLoaded ? FeatureResetRuntimeOutcome.RESTORED : FeatureResetRuntimeOutcome.UNCHANGED)
                        : FeatureResetRuntimeOutcome.DEGRADED,
                restored ? FeatureResetRollbackOutcome.SUCCEEDED : FeatureResetRollbackOutcome.FAILED,
                dependents, deletedOverrides, backup.id(), failure);
    }

    private boolean restoreGraphAfterPreMutationFailure(
            boolean wasLoaded,
            List<String> order,
            Map<String, Optional<SnapshotState>> states
    ) {
        boolean restored = !wasLoaded || controller.startReloadGraph(order, states);
        if (wasLoaded) afterGraphMutation.run();
        return restored;
    }

    private static FeatureFileResetResponse resetResponse(
            FeatureFileResetResult result,
            String feature,
            FeatureFileResetRequest request,
            boolean committed,
            FeatureResetRuntimeOutcome runtimeOutcome,
            FeatureResetRollbackOutcome rollbackOutcome,
            Set<String> dependents,
            List<String> deletedOverrides,
            String backupId,
            Throwable failure
    ) {
        return new FeatureFileResetResponse(result, feature, request, committed, runtimeOutcome, rollbackOutcome,
                dependents, deletedOverrides, Optional.ofNullable(backupId), Optional.ofNullable(failure));
    }

    private FeatureGraphReloadResult reloadLocked() {
        runtime.markReloading();
        FeatureGraphReloadResult result = FeatureGraphReloader.reload(
                reloadHostResources,
                () -> { },
                inventory.registry()::getLoadedFeatureNames,
                inventory.registry()::isFeatureLoaded,
                configuration::isFeatureEnabled,
                this::disableFeatureLocked,
                this::recreateLocked,
                () -> inventory.registry().getAvailableFeatures().keySet(),
                this::enableLockedId
        );
        if (result.success()) runtime.markReady();
        else runtime.markDegraded();
        return result;
    }

    private FeatureReloadResponse recreateLocked(String featureName) {
        FeatureReloadResponse response = controller.reloadFeature(featureName);
        afterGraphMutation.run();
        return response;
    }

    /** Stops every feature in reverse lifecycle sequence and marks the runtime stopped. */
    public void stop() {
        runtime.lifecycle().runExclusive(this::stopLocked);
    }

    private synchronized void stopLocked() {
        FeatureFrameworkObservations.Operation observation = observations.start(FeatureFrameworkOperationKind.HOST_STOP);
        try (FeatureFrameworkObservationScope ignored = observation.openScope()) {
            if (runtime.state() == RuntimeState.STOPPED) {
                observation.complete(FeatureFrameworkOperationOutcome.NO_CHANGE, null);
                return;
            }
            runtime.markStopping();
            Throwable failure = unloadAll();
            runtime.markStopped(failure);
            if (failure != null) logger.error(hostName + " shutdown completed with failures.", failure);
            observation.complete(
                    failure == null ? FeatureFrameworkOperationOutcome.SUCCESS : FeatureFrameworkOperationOutcome.FAILURE,
                    failure
            );
        } catch (Throwable failure) {
            observation.complete(FeatureFrameworkOperationOutcome.FAILURE, failure);
            throwUnchecked(failure);
        }
    }

    private Throwable unloadAll() {
        Throwable failure = null;
        List<String> loaded = new ArrayList<>(inventory.registry().getLoadedFeatureNames());
        Collections.reverse(loaded);
        for (String key : loaded) {
            Throwable featureFailure = controller.stopAndRemove(key);
            controller.completeDisable(key, featureFailure, "shutdown");
            failure = appendFailure(failure, featureFailure);
        }
        clearScopes.run();
        afterGraphMutation.run();
        return failure;
    }

    public Optional<FeatureId> resolve(String inputName) {
        if (inputName == null || inputName.isBlank()) return Optional.empty();
        String key = inventory.resolveFeatureKey(inputName);
        return key == null ? Optional.empty() : Optional.of(FeatureId.of(key));
    }

    public boolean isLoaded(FeatureId id) {
        return inventory.registry().isFeatureLoaded(Objects.requireNonNull(id, "id").value());
    }

    public Optional<F> findLoaded(FeatureId id) {
        return Optional.ofNullable(inventory.registry().getLoadedFeature(Objects.requireNonNull(id, "id").value()));
    }

    public List<F> loadedFeatures() {
        return List.copyOf(inventory.registry().getLoadedFeatures());
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
        private Function<ResolvedFeatureDefinition<F, C>, C> contextFactory;
        private Predicate<String> pluginAvailable = ignored -> true;
        private Runnable afterGraphMutation = () -> { };
        private Runnable clearScopes = () -> { };
        private Runnable reloadHostResources;
        private FrameworkLogger logger = FrameworkLogger.noop();
        private FeatureFrameworkObserver observer = FeatureFrameworkObserver.noop();

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

        public Builder<V, F, C> contextFactory(Function<ResolvedFeatureDefinition<F, C>, C> value) {
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

        public Builder<V, F, C> observer(FeatureFrameworkObserver value) {
            observer = Objects.requireNonNull(value, "observer");
            return this;
        }

        public FeatureHost<V, F, C> build() {
            return new FeatureHost<>(this);
        }
    }

    private static FeatureFrameworkOperationOutcome enableOutcome(FeatureEnableResponse response) {
        return switch (response.result()) {
            case SUCCESS -> FeatureFrameworkOperationOutcome.SUCCESS;
            case ALREADY_LOADED -> FeatureFrameworkOperationOutcome.NO_CHANGE;
            case NOT_FOUND, MISSING_PLUGIN_DEPENDENCY, MISSING_FEATURE_DEPENDENCY ->
                    FeatureFrameworkOperationOutcome.SKIPPED;
            case FAILED -> FeatureFrameworkOperationOutcome.FAILURE;
        };
    }

    private static FeatureFrameworkOperationOutcome disableOutcome(FeatureDisableResponse response) {
        return switch (response.result()) {
            case SUCCESS -> FeatureFrameworkOperationOutcome.SUCCESS;
            case NOT_LOADED -> FeatureFrameworkOperationOutcome.NO_CHANGE;
            case FAILED -> FeatureFrameworkOperationOutcome.FAILURE;
        };
    }

    private static FeatureFrameworkOperationOutcome reloadOutcome(FeatureReloadResponse response) {
        return switch (response.result()) {
            case SUCCESS -> FeatureFrameworkOperationOutcome.SUCCESS;
            case NOT_LOADED -> FeatureFrameworkOperationOutcome.SKIPPED;
            case FAILED -> FeatureFrameworkOperationOutcome.FAILURE;
        };
    }

    private static FeatureFrameworkOperationOutcome softReloadOutcome(FeatureSoftReloadResponse response) {
        return switch (response.result()) {
            case SUCCESS -> FeatureFrameworkOperationOutcome.SUCCESS;
            case NOT_LOADED -> FeatureFrameworkOperationOutcome.SKIPPED;
            case FAILED -> FeatureFrameworkOperationOutcome.FAILURE;
        };
    }

    private static FeatureFrameworkOperationOutcome resetOutcome(FeatureFileResetResponse response) {
        return switch (response.result()) {
            case SUCCESS -> FeatureFrameworkOperationOutcome.SUCCESS;
            case NOT_FOUND, HOST_UNAVAILABLE, UNSAFE_TARGET -> FeatureFrameworkOperationOutcome.SKIPPED;
            case QUIESCE_FAILED, BACKUP_FAILED, REGENERATION_FAILED, RESTART_FAILED, ROLLBACK_FAILED ->
                    FeatureFrameworkOperationOutcome.FAILURE;
        };
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
