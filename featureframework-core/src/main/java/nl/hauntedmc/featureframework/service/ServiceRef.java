package nl.hauntedmc.featureframework.service;

import java.util.Optional;
import java.util.OptionalLong;

/**
 * Reload-safe reference to an optional feature service.
 *
 * <p>Consumers must resolve the reference for each independent operation. The reference itself is
 * stable, while its provider may be enabled, disabled, or replaced by a feature reload.</p>
 */
public interface ServiceRef<T> {
    Class<T> type();

    Optional<T> get();

    OptionalLong generation();

    default boolean isAvailable() {
        return get().isPresent();
    }

    default T require() {
        return get().orElseThrow(() -> new IllegalStateException(
                "Optional feature service is currently unavailable: " + type().getName()));
    }
}
