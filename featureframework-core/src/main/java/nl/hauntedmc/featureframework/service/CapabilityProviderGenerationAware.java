package nl.hauntedmc.featureframework.service;

/** Hook for a public capability that needs its publication generation in exposed snapshots. */
public interface CapabilityProviderGenerationAware {

    /** Called before the provider becomes visible through the capability registry. */
    void providerGeneration(long generation);
}
