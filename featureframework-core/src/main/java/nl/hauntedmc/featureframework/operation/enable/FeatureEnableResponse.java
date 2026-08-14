package nl.hauntedmc.featureframework.operation.enable;

import java.util.Set;

public record FeatureEnableResponse(
        FeatureEnableResult result,
        Set<String> missingPlugins,
        Set<String> missingFeatures
) {
    public boolean success() {
        return result == FeatureEnableResult.SUCCESS;
    }
}
