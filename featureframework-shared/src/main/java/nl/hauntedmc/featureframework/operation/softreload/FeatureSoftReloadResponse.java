package nl.hauntedmc.featureframework.operation.softreload;

public record FeatureSoftReloadResponse(
        FeatureSoftReloadResult result,
        String feature
) {
    public boolean success() {
        return result == FeatureSoftReloadResult.SUCCESS;
    }
}
