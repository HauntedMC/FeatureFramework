package nl.hauntedmc.featureframework.lifecycle;

/**
 * Platform-independent lifecycle operations that every feature-owned resource scope exposes.
 *
 * <p>This lets the shared feature base enforce one shutdown order while Paper and Velocity keep
 * their platform-specific resource implementations.</p>
 */
public interface FeatureLifecycleResources {
    void quiesce();
    void cleanup();
}
