package nl.hauntedmc.featureframework.host;

import nl.hauntedmc.featureframework.api.FeatureFrameworkApi;
import nl.hauntedmc.featureframework.api.RuntimeState;
import nl.hauntedmc.featureframework.api.feature.FeatureCatalog;
import nl.hauntedmc.featureframework.api.feature.FeatureId;
import nl.hauntedmc.featureframework.api.service.CapabilityRegistry;
import nl.hauntedmc.featureframework.config.FeatureConfigurationRoot;
import nl.hauntedmc.featureframework.feature.LifecycleFeature;
import nl.hauntedmc.featureframework.loader.FeatureDescriptor;
import nl.hauntedmc.featureframework.loader.FeatureLoadOrderResolver;
import nl.hauntedmc.featureframework.loader.FeatureRegistry;
import nl.hauntedmc.featureframework.operation.FeatureOperationCoordinator;
import nl.hauntedmc.featureframework.operation.disable.FeatureDisableResponse;
import nl.hauntedmc.featureframework.operation.enable.FeatureEnableResponse;
import nl.hauntedmc.featureframework.operation.reload.FeatureGraphReloadResult;
import nl.hauntedmc.featureframework.operation.reload.FeatureGraphReloader;
import nl.hauntedmc.featureframework.operation.reload.FeatureReloadResponse;
import nl.hauntedmc.featureframework.operation.softreload.FeatureSoftReloadResponse;
import nl.hauntedmc.featureframework.runtime.FeatureRuntime;
import nl.hauntedmc.featureframework.service.DefaultCapabilityRegistry;
import nl.hauntedmc.featureframework.toolkit.log.FrameworkLogger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletionStage;
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
public final class FeatureHost<V, F extends LifecycleFeature<C>, C extends FeatureHostContext>
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
    private boolean startAttempted;

    private FeatureHost(Builder<V, F, C> builder) {
        hostName = requireText(builder.hostName, "hostName");
        version = Objects.requireNonNull(builder.version, "version");
        String capabilityNamespace = requireText(builder.capabilityNamespace, "capabilityNamespace");
        runtime = Objects.requireNonNull(builder.runtime, "runtime");
        configuration = Objects.requireNonNull(builder.configuration, "configuration");
        FeatureCollection<F, C> collection = Objects.requireNonNull(builder.collection, "collection");
        Function<FeatureDescriptor<F, C>, C> contextFactory =
                Objects.requireNonNull(builder.contextFactory, "contextFactory");
        Predicate<String> pluginAvailable = Objects.requireNonNull(builder.pluginAvailable, "pluginAvailable");
        afterGraphMutation = Objects.requireNonNull(builder.afterGraphMutation, "afterGraphMutation");
        clearScopes = Objects.requireNonNull(builder.clearScopes, "clearScopes");
        reloadHostResources = builder.reloadHostResources == null
                ? configuration::reloadConfig
                : builder.reloadHostResources;
        logger = Objects.requireNonNull(builder.logger, "logger");
        inventory = new FeatureInventory<>(
                capabilityNamespace, runtime, configuration, collection, pluginAvailable, logger);
        controller = new FeatureInstanceController<>(inventory, runtime, configuration, contextFactory, logger);
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

    public FeatureEnableResponse enableFeature(String featureName) {
        return runtime.lifecycle().callExclusive(() -> FeatureOperationCoordinator.enable(
                featureName,
                inventory::resolveFeatureKey,
                key -> inventory.registry().getAvailableFeature(key) != null,
                inventory.registry()::isFeatureLoaded,
                inventory::diagnoseDependencies,
                configuration::isFeatureEnabled,
                this::persistEnabled,
                controller::loadFeature,
                afterGraphMutation
        ));
    }

    public FeatureDisableResponse disableFeature(String featureName) {
        return runtime.lifecycle().callExclusive(() -> disableFeatureLocked(featureName));
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

    public FeatureSoftReloadResponse softReloadFeature(String featureName) {
        return runtime.lifecycle().callExclusive(() -> FeatureOperationCoordinator.softReload(
                featureName,
                inventory::resolveFeatureKey,
                inventory.registry()::isFeatureLoaded,
                key -> {
                    F feature = controller.loadedFeature(key);
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
                inventory.registry()::getLoadedFeatureNames,
                inventory.registry()::isFeatureLoaded,
                configuration::isFeatureEnabled,
                this::disableFeatureLocked,
                this::reloadFeatureLocked,
                () -> inventory.registry().getAvailableFeatures().keySet(),
                this::enableFeature
        );
        if (result.success()) runtime.markReady();
        else runtime.markDegraded();
        return result;
    }

    private FeatureReloadResponse reloadFeatureLocked(String featureName) {
        FeatureReloadResponse response = controller.reloadFeature(featureName);
        afterGraphMutation.run();
        return response;
    }

    public boolean loadFeature(String featureName) {
        return runtime.lifecycle().callExclusive(() -> controller.loadFeature(featureName));
    }

    /** Stops every feature in reverse lifecycle sequence and marks the runtime stopped. */
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

    public Set<String> missingPluginDependencies(String featureName) {
        return inventory.missingPluginDependencies(featureName);
    }

    public String resolveFeatureKey(String inputName) {
        return inventory.resolveFeatureKey(inputName);
    }

    public FeatureRegistry<F, FeatureDescriptor<F, C>> registry() {
        return inventory.registry();
    }

    public boolean isLoaded(String featureName) {
        String key = inventory.resolveFeatureKey(featureName);
        return key != null && inventory.registry().isFeatureLoaded(key);
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
