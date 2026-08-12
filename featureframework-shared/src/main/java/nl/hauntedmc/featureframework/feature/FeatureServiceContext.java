package nl.hauntedmc.featureframework.feature;

import nl.hauntedmc.featureframework.api.service.CapabilityRegistry;
import nl.hauntedmc.featureframework.service.InternalServiceRegistry;

/** Runtime context contract that gives a feature access to its host's public and internal service registries. */
public interface FeatureServiceContext extends FeatureContextMetadata {
    CapabilityRegistry capabilities();
    InternalServiceRegistry<?> internalServices();
}
