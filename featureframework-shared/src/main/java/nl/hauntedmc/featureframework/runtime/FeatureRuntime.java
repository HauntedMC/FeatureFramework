package nl.hauntedmc.featureframework.runtime;

import nl.hauntedmc.featureframework.api.RuntimeState;
import nl.hauntedmc.featureframework.api.feature.FeatureCatalog;
import nl.hauntedmc.featureframework.api.service.CapabilityRegistry;
import nl.hauntedmc.featureframework.lifecycle.LifecycleCoordinator;
import nl.hauntedmc.featureframework.service.DefaultFeatureCatalog;
import nl.hauntedmc.featureframework.service.InternalServiceRegistry;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Stable root-runtime state owned by every FeatureFramework host. This keeps public API state,
 * readiness, registries, and lifecycle serialization identical across platforms.
 */
public final class FeatureRuntime<O, C extends CapabilityRegistry> {
    private final String hostName;
    private final C capabilities;
    private final DefaultFeatureCatalog features = new DefaultFeatureCatalog();
    private final InternalServiceRegistry<O> internalServices = new InternalServiceRegistry<>();
    private final LifecycleCoordinator lifecycle = new LifecycleCoordinator();
    private final CompletableFuture<Void> ready = new CompletableFuture<>();
    private volatile RuntimeState state = RuntimeState.STARTING;

    public FeatureRuntime(String hostName, C capabilities) {
        this.hostName = requireText(hostName, "hostName");
        this.capabilities = Objects.requireNonNull(capabilities, "capabilities");
    }

    public RuntimeState state() {
        return state;
    }

    public CompletionStage<Void> whenReady() {
        return ready.minimalCompletionStage();
    }

    public C capabilities() {
        return capabilities;
    }

    public FeatureCatalog featureCatalog() {
        return features;
    }

    public DefaultFeatureCatalog mutableFeatureCatalog() {
        return features;
    }

    public InternalServiceRegistry<O> internalServices() {
        return internalServices;
    }

    public LifecycleCoordinator lifecycle() {
        return lifecycle;
    }

    public void markStarting() {
        state = RuntimeState.STARTING;
    }

    public void markReady() {
        state = RuntimeState.READY;
        ready.complete(null);
    }

    public void markReloading() {
        state = RuntimeState.RELOADING;
    }

    public void markDegraded(Throwable failure) {
        state = RuntimeState.DEGRADED;
        if (!ready.isDone()) {
            ready.completeExceptionally(Objects.requireNonNull(failure, "failure"));
        }
    }

    /** Marks a previously-ready host degraded after an operational failure. */
    public void markDegraded() {
        state = RuntimeState.DEGRADED;
    }

    public void markStopping() {
        state = RuntimeState.STOPPING;
    }

    public void markStopped(Throwable startupOrShutdownFailure) {
        state = RuntimeState.STOPPED;
        if (!ready.isDone()) {
            ready.completeExceptionally(startupOrShutdownFailure == null
                    ? new IllegalStateException(hostName + " stopped before becoming ready")
                    : startupOrShutdownFailure);
        }
    }

    private static String requireText(String value, String name) {
        String normalized = Objects.requireNonNull(value, name).trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(name + " must not be blank");
        return normalized;
    }
}
