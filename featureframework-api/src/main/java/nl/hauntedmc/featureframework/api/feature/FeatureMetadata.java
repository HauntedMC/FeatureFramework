package nl.hauntedmc.featureframework.api.feature;

import java.util.Objects;
import java.util.Set;

/**
 * Immutable, implementation-free metadata published through the public feature catalog.
 *
 * <p>This type describes a feature to API consumers and intentionally contains no implementation
 * class or construction callback. It is distinct from the host construction descriptor named
 * {@code nl.hauntedmc.featureframework.loader.ResolvedFeatureDefinition}.</p>
 */
public record FeatureMetadata(
        FeatureId id,
        String displayName,
        String version,
        Set<FeatureId> requiredFeatures,
        Set<String> requiredPlugins,
        Set<String> requiredResourceExtensions,
        Set<String> providedCapabilities,
        Set<FeatureRole> roles
) {
    public FeatureMetadata {
        Objects.requireNonNull(id, "id");
        displayName = requireText(displayName, "displayName");
        version = requireText(version, "version");
        requiredFeatures = requiredFeatures == null ? Set.of() : Set.copyOf(requiredFeatures);
        requiredPlugins = requiredPlugins == null ? Set.of() : Set.copyOf(requiredPlugins);
        requiredResourceExtensions = requiredResourceExtensions == null
                ? Set.of() : Set.copyOf(requiredResourceExtensions);
        providedCapabilities = providedCapabilities == null ? Set.of() : Set.copyOf(providedCapabilities);
        roles = roles == null ? Set.of() : Set.copyOf(roles);
    }

    /** Creates metadata for a host that does not expose an additional classification policy. */
    public FeatureMetadata(
            FeatureId id,
            String displayName,
            String version,
            Set<FeatureId> requiredFeatures,
            Set<String> providedCapabilities,
            Set<FeatureRole> roles
    ) {
        this(
                id,
                displayName,
                version,
                requiredFeatures,
                Set.of(),
                Set.of(),
                providedCapabilities,
                roles
        );
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }
}
