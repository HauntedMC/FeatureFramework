package nl.hauntedmc.featureframework.operation.reload;

import java.util.Optional;

/** Outcome of reloading a configured feature graph. */
public record FeatureGraphReloadResult(
        Stage stage,
        Optional<String> feature,
        Optional<Throwable> failure
) {
    public enum Stage {
        SUCCESS,
        CONFIGURATION,
        DISABLE,
        RELOAD
    }

    public FeatureGraphReloadResult {
        feature = feature == null ? Optional.empty() : feature;
        failure = failure == null ? Optional.empty() : failure;
    }

    public boolean success() {
        return stage == Stage.SUCCESS;
    }

    static FeatureGraphReloadResult successResult() {
        return new FeatureGraphReloadResult(Stage.SUCCESS, Optional.empty(), Optional.empty());
    }

    static FeatureGraphReloadResult failed(Stage stage, String feature, Throwable failure) {
        return new FeatureGraphReloadResult(stage, Optional.ofNullable(feature), Optional.ofNullable(failure));
    }
}
