package nl.hauntedmc.featureframework.api.feature;

import java.util.Objects;
import java.util.Optional;

/** Structured explanation for an enabled feature that is deliberately not active. */
public record FeatureSuppression(FeatureSuppressionReason reason, Optional<String> message) {
    public FeatureSuppression {
        reason = Objects.requireNonNull(reason, "reason");
        message = message == null ? Optional.empty() : message.map(String::trim).filter(value -> !value.isEmpty());
    }

    public FeatureSuppression(FeatureSuppressionReason reason, String message) {
        this(reason, Optional.ofNullable(message));
    }

    public static FeatureSuppression of(FeatureSuppressionReason reason) {
        return new FeatureSuppression(reason, Optional.empty());
    }
}
