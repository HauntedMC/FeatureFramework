package nl.hauntedmc.featureframework.api.service;

import java.util.Optional;
import java.util.OptionalLong;

/**
 * Reload-safe reference to an optional capability.
 *
 * <p>Callers should resolve {@link #get()} for each operation instead of retaining an implementation
 * returned before a feature reload. Implementations may return a stable invocation proxy that resolves
 * and leases the active provider for each method call.</p>
 */
public interface CapabilityRef<T> {
    /** The public contract represented by this reference. */
    Class<T> type();

    /** Returns the currently active implementation, if its provider is enabled. */
    Optional<T> get();

    /**
     * Returns the generation of the currently active provider. A new generation is assigned whenever
     * a provider is republished after reload.
     */
    default OptionalLong generation() {
        return isAvailable() ? OptionalLong.of(1L) : OptionalLong.empty();
    }

    /** Returns whether the capability currently has an active provider. */
    default boolean isAvailable() {
        return get().isPresent();
    }

    /** Resolves the current implementation or fails with a descriptive exception. */
    default T require() {
        return get().orElseThrow(() -> new CapabilityUnavailableException(type()));
    }
}
