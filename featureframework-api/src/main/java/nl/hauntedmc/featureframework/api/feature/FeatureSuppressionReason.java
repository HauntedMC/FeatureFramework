package nl.hauntedmc.featureframework.api.feature;

/** Stable reasons why an enabled feature is intentionally not running. */
public enum FeatureSuppressionReason {
    GROUP_LEADER_ONLY,
    AUTHORITY_UNAVAILABLE,
    DEPENDENCY_SUPPRESSED,
    CONFIGURATION_UNAVAILABLE,
    CONFIGURATION_INCOMPATIBLE
}
