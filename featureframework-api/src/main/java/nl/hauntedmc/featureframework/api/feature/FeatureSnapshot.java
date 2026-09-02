package nl.hauntedmc.featureframework.api.feature;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Point-in-time public view of one feature and its lifecycle state. */
public record FeatureSnapshot(
        FeatureMetadata metadata,
        boolean configuredEnabled,
        FeatureState state,
        Optional<FeatureSuppression> suppression,
        Optional<String> failure,
        Optional<FeatureFailure> failureDetail,
        Set<FeatureId> unavailableDependencies,
        Instant lastTransitionAt,
        Optional<Instant> lastSuccessfulActivationAt,
        long generation,
        Instant observedAt
) {
    public FeatureSnapshot {
        Objects.requireNonNull(metadata, "metadata");
        state = Objects.requireNonNull(state, "state");
        suppression = suppression == null ? Optional.empty() : suppression;
        failure = failure == null ? Optional.empty() : failure.filter(value -> !value.isBlank());
        failureDetail = failureDetail == null ? Optional.empty() : failureDetail;
        unavailableDependencies = unavailableDependencies == null ? Set.of() : Set.copyOf(unavailableDependencies);
        lastTransitionAt = Objects.requireNonNull(lastTransitionAt, "lastTransitionAt");
        lastSuccessfulActivationAt = lastSuccessfulActivationAt == null ? Optional.empty() : lastSuccessfulActivationAt;
        if (generation < 0) throw new IllegalArgumentException("generation must be non-negative");
        observedAt = Objects.requireNonNull(observedAt, "observedAt");
        if (state == FeatureState.SUPPRESSED && suppression.isEmpty()) {
            throw new IllegalArgumentException("SUPPRESSED state requires suppression detail");
        }
        if (state != FeatureState.SUPPRESSED && suppression.isPresent()) {
            throw new IllegalArgumentException("suppression detail is only valid for SUPPRESSED state");
        }
    }

    /** Compatibility constructor for callers that do not project suppression. */
    public FeatureSnapshot(
            FeatureMetadata metadata,
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
        this(metadata, configuredEnabled, state, Optional.empty(), failure, failureDetail, unavailableDependencies,
                lastTransitionAt, lastSuccessfulActivationAt, generation, observedAt);
    }

    /** Creates a snapshot from the typed failure projection used by framework hosts. */
    public FeatureSnapshot(
            FeatureMetadata metadata,
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
                metadata,
                configuredEnabled,
                state,
                Optional.empty(),
                failureDetail == null ? Optional.empty() : failureDetail.flatMap(FeatureFailure::message),
                failureDetail,
                unavailableDependencies,
                lastTransitionAt,
                lastSuccessfulActivationAt,
                generation,
                observedAt
        );
    }

    public boolean active() {
        return state == FeatureState.ACTIVE;
    }

    public boolean suppressed() {
        return state == FeatureState.SUPPRESSED;
    }

    public boolean failed() {
        return state == FeatureState.FAILED;
    }
}
