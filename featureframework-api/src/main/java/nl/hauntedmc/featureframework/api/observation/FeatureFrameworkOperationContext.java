package nl.hauntedmc.featureframework.api.observation;

import nl.hauntedmc.featureframework.api.feature.FeatureId;

import java.util.Objects;
import java.util.Optional;

/**
 * Stable, payload-free metadata for one FeatureFramework operation.
 *
 * <p>The context deliberately contains only a bounded operation kind and, for feature-scoped
 * operations, the framework-owned {@link FeatureId}. Configuration values, file paths, plugin
 * objects, dependency lists, command input, player data, and arbitrary caller strings do not belong here.</p>
 */
public record FeatureFrameworkOperationContext(
        FeatureFrameworkOperationKind operation,
        Optional<FeatureId> featureId
) {

    public FeatureFrameworkOperationContext {
        Objects.requireNonNull(operation, "operation");
        featureId = featureId == null ? Optional.empty() : featureId;
        if (operation.featureScoped() != featureId.isPresent()) {
            throw new IllegalArgumentException(
                    operation.featureScoped()
                            ? "Feature-scoped operations require a FeatureId."
                            : "Host-scoped operations must not include a FeatureId."
            );
        }
    }

    /** Creates a host-scoped context. */
    public static FeatureFrameworkOperationContext host(FeatureFrameworkOperationKind operation) {
        return new FeatureFrameworkOperationContext(operation, Optional.empty());
    }

    /** Creates a feature-scoped context. */
    public static FeatureFrameworkOperationContext feature(
            FeatureFrameworkOperationKind operation,
            FeatureId featureId
    ) {
        return new FeatureFrameworkOperationContext(operation, Optional.of(Objects.requireNonNull(featureId, "featureId")));
    }
}
