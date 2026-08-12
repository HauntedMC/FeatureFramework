package nl.hauntedmc.featureframework.api.service;

import java.util.Set;

/** Read-only catalog of feature capabilities provided by the current runtime. */
public interface CapabilityRegistry {
    /** Returns a stable reference for the requested public contract. */
    <T> CapabilityRef<T> reference(Class<T> type);

    /** Returns the contracts that currently have an active provider. */
    Set<Class<?>> availableTypes();

    /**
     * Registers a lifecycle listener. Close the returned subscription when the owning plugin
     * unloads; platform adapters must do this automatically for plugin-owned registrations.
     */
    AutoCloseable subscribe(CapabilityListener listener);
}
