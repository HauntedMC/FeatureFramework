package nl.hauntedmc.featureframework.api;

/** Stable machine-readable reasons for failed public API operations. */
public enum ApiFailureCode {
    FEATURE_UNAVAILABLE,
    PROVIDER_RELOADED,
    REQUEST_INVALID,
    PLAYER_OFFLINE,
    PERSISTENCE_UNAVAILABLE,
    TIMEOUT,
    CANCELLED,
    PERMISSION_DENIED,
    INTERNAL_FAILURE
}
