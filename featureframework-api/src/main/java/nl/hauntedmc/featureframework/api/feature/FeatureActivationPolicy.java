package nl.hauntedmc.featureframework.api.feature;

import java.util.Objects;

/** Host-supplied policy that decides whether a feature may be prepared or activated. */
@FunctionalInterface
public interface FeatureActivationPolicy {
    ActivationDecision evaluate(FeatureMetadata metadata, FeatureActivationPhase phase);

    static FeatureActivationPolicy allowAll() {
        return (metadata, phase) -> {
            Objects.requireNonNull(metadata, "metadata");
            Objects.requireNonNull(phase, "phase");
            return ActivationDecision.allow();
        };
    }
}
