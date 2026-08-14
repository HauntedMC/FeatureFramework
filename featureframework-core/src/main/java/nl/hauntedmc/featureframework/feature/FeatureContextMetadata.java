package nl.hauntedmc.featureframework.feature;

import java.util.List;

/** Neutral metadata view required by the shared base feature implementation. */
public interface FeatureContextMetadata {
    String featureName();
    String featureVersion();
    List<String> featureDependencies();
    default List<String> optionalFeatureDependencies() { return List.of(); }
    List<String> pluginDependencies();
}
