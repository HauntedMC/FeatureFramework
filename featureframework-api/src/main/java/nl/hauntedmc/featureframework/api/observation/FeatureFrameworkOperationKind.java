package nl.hauntedmc.featureframework.api.observation;

/** Stable, low-cardinality operation kinds exposed by FeatureFramework observation. */
public enum FeatureFrameworkOperationKind {
    HOST_START(false),
    HOST_STOP(false),
    FEATURE_LOAD(true),
    FEATURE_ENABLE(true),
    FEATURE_DISABLE(true),
    FEATURE_RECREATE(true),
    FEATURE_SOFT_RELOAD(true),
    GRAPH_RELOAD(false),
    FILE_RESET(true);

    private final boolean featureScoped;

    FeatureFrameworkOperationKind(boolean featureScoped) {
        this.featureScoped = featureScoped;
    }

    /** Returns whether this operation must identify one FeatureFramework feature. */
    public boolean featureScoped() {
        return featureScoped;
    }
}
