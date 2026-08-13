package nl.hauntedmc.featureframework.api.feature;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Point-in-time public view of one feature and its lifecycle state. */
public record FeatureSnapshot(
        FeatureDescriptor descriptor,
        boolean configuredEnabled,
        FeatureState state,
        Optional<String> failure,
        Optional<FeatureFailure> failureDetail,
        Set<FeatureId> unavailableDependencies,
        Instant lastTransitionAt,
        Optional<Instant> lastSuccessfulActivationAt,
        long generation,
        Instant observedAt
) {
    public FeatureSnapshot {
        Objects.requireNonNull(descriptor, "descriptor");
        state = Objects.requireNonNull(state, "state");
        failure = failure == null ? Optional.empty() : failure.filter(value -> !value.isBlank());
        failureDetail = failureDetail == null ? Optional.empty() : failureDetail;
        unavailableDependencies = unavailableDependencies == null ? Set.of() : Set.copyOf(unavailableDependencies);
        lastTransitionAt = Objects.requireNonNull(lastTransitionAt, "lastTransitionAt");
        lastSuccessfulActivationAt = lastSuccessfulActivationAt == null ? Optional.empty() : lastSuccessfulActivationAt;
        if (generation < 0) throw new IllegalArgumentException("generation must be non-negative");
        observedAt = Objects.requireNonNull(observedAt, "observedAt");
    }

    /** Creates a snapshot from the typed failure projection used by framework hosts. */
    public FeatureSnapshot(
            FeatureDescriptor descriptor,
            boolean configuredEnabled,
            FeatureState state,
            Optional<FeatureFailure> failureDetail,
            Set<FeatureId> unavailableDependencies,
            Instant lastTransitionAt,
            Optional<Instant> lastSuccessfulActivationAt,
            long generation,
            Instant observedAt
    ) {
        this(
                descriptor,
                configuredEnabled,
                state,
                failureDetail == null ? Optional.empty() : failureDetail.flatMap(FeatureFailure::message),
                failureDetail,
                unavailableDependencies,
                lastTransitionAt,
                lastSuccessfulActivationAt,
                generation,
                observedAt
        );
    }

}
