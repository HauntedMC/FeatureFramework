package nl.hauntedmc.featureframework.api.feature;

/**
 * A readable startup phase for otherwise independent features.
 *
 * <p>Constants are declared in lifecycle order. Required feature and capability relationships
 * always take precedence; a phase only provides deterministic ordering when no such relationship
 * exists.</p>
 */
public enum FeatureStartupPhase {
    /** Foundational features that establish shared state or essential capabilities. */
    FOUNDATION,
    /** Security, access-control, and policy-enforcement features. */
    SECURITY,
    /** The normal application feature phase. */
    CORE,
    /** Player-facing presentation and interaction features. */
    PRESENTATION,
    /** Operational, administration, and maintenance features. */
    OPERATIONS,
    /** Non-critical features that should start after the primary application is available. */
    DEFERRED
}
