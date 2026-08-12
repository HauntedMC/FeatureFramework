package nl.hauntedmc.featureframework.api.feature;

import java.util.Objects;
import java.util.Set;

/** Immutable public metadata for a built-in feature. */
public record FeatureDescriptor(
        FeatureId id,
        String displayName,
        String version,
        FeatureClassification classification,
        Set<FeatureId> requiredFeatures,
        Set<String> providedCapabilities,
        Set<FeatureRole> roles
) {
    public FeatureDescriptor {
        Objects.requireNonNull(id, "id");
        displayName = requireText(displayName, "displayName");
        version = requireText(version, "version");
        classification = Objects.requireNonNull(classification, "classification");
        requiredFeatures = requiredFeatures == null ? Set.of() : Set.copyOf(requiredFeatures);
        providedCapabilities = providedCapabilities == null ? Set.of() : Set.copyOf(providedCapabilities);
        roles = roles == null ? Set.of() : Set.copyOf(roles);
    }

    /** Creates metadata for a host that does not expose an additional classification policy. */
    public FeatureDescriptor(
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
                FeatureClassification.INTERNAL,
                requiredFeatures,
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
