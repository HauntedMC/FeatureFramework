package nl.hauntedmc.featureframework.config;

/** Outcome of applying changed configuration to a running feature. */
public enum ConfigReloadResult {
    APPLIED,
    RECREATE_REQUIRED
}
