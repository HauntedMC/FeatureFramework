package nl.hauntedmc.featureframework.resource;

import nl.hauntedmc.featureframework.api.feature.FeatureId;
import nl.hauntedmc.featureframework.loader.ResolvedFeatureDefinition;

import java.util.Objects;
import java.util.Set;

/** Immutable declaration context for one feature resource generation. */
public record FeatureResourceRequest(
        FeatureId id,
        String displayName,
        Set<Class<?>> requiredExtensions,
        Set<Class<?>> optionalExtensions
) {
    public FeatureResourceRequest {
        Objects.requireNonNull(id, "id");
        displayName = requireText(displayName, "displayName");
        requiredExtensions = requiredExtensions == null ? Set.of() : Set.copyOf(requiredExtensions);
        optionalExtensions = optionalExtensions == null ? Set.of() : Set.copyOf(optionalExtensions);
    }

    public static FeatureResourceRequest from(ResolvedFeatureDefinition<?, ?> definition) {
        Objects.requireNonNull(definition, "definition");
        return new FeatureResourceRequest(
                FeatureId.of(definition.registryName()),
                definition.featureName(),
                definition.requiredResourceExtensions(),
                definition.optionalResourceExtensions()
        );
    }

    /** Whether this feature declaration asks the host to provide {@code extensionType}. */
    public boolean requests(Class<?> extensionType) {
        Objects.requireNonNull(extensionType, "extensionType");
        return requiredExtensions.contains(extensionType) || optionalExtensions.contains(extensionType);
    }

    private static String requireText(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " must not be blank");
        return normalized;
    }
}
