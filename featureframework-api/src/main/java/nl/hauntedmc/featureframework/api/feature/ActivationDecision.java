package nl.hauntedmc.featureframework.api.feature;

import java.util.Objects;
import java.util.Optional;

/** Result of evaluating whether one feature may proceed in a lifecycle phase. */
public record ActivationDecision(boolean allowed, Optional<FeatureSuppression> suppression) {
    public ActivationDecision {
        suppression = suppression == null ? Optional.empty() : suppression;
        if (allowed && suppression.isPresent()) {
            throw new IllegalArgumentException("An allowed activation decision cannot carry suppression");
        }
        if (!allowed && suppression.isEmpty()) {
            throw new IllegalArgumentException("A denied activation decision must carry suppression");
        }
    }

    public static ActivationDecision allow() {
        return new ActivationDecision(true, Optional.empty());
    }

    public static ActivationDecision suppress(FeatureSuppression suppression) {
        return new ActivationDecision(false, Optional.of(Objects.requireNonNull(suppression, "suppression")));
    }

    public static ActivationDecision suppress(FeatureSuppressionReason reason, String message) {
        return suppress(new FeatureSuppression(reason, message));
    }
}
