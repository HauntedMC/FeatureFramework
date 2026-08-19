package nl.hauntedmc.featureframework.api.service;

import java.util.Optional;
import java.util.Set;

/** Read-only catalog of feature capabilities provided by the current runtime. */
public interface CapabilityRegistry {
    /** Returns a stable reference for the requested public contract. */
    <T> CapabilityRef<T> reference(Class<T> type);

    /** Resolves the currently active implementation, if available. */
    default <T> Optional<T> findCapability(Class<T> type) {
        return reference(type).get();
    }

    /** Resolves the currently active implementation or fails with a descriptive exception. */
    default <T> T requireCapability(Class<T> type) {
        return reference(type).require();
    }

    /** Returns whether the requested contract currently has an active provider. */
    default boolean hasCapability(Class<?> type) {
        return reference(type).isAvailable();
    }

    /** Returns the contracts that currently have an active provider. */
    Set<Class<?>> availableTypes();

    /**
     * Registers a lifecycle listener. Close the returned subscription when the owning plugin
     * unloads; platform adapters must do this automatically for plugin-owned registrations.
     */
    AutoCloseable subscribe(CapabilityListener listener);
}
