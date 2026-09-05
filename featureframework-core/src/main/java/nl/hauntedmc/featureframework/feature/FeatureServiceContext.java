package nl.hauntedmc.featureframework.feature;

import nl.hauntedmc.featureframework.service.FeatureServices;

/** Runtime context contract exposing the feature's declaration-aware service boundary. */
public interface FeatureServiceContext extends FeatureContextMetadata {
    FeatureServices featureServices();
}
