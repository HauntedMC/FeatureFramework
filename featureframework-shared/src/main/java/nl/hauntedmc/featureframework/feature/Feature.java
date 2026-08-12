package nl.hauntedmc.featureframework.feature;

import nl.hauntedmc.featureframework.toolkit.io.config.ConfigMap;
import nl.hauntedmc.featureframework.toolkit.io.localization.MessageMap;

import java.util.List;

/** Platform-neutral contract implemented by every managed feature. */
public interface Feature {
    String getFeatureName();
    String getFeatureVersion();
    List<String> getDependencies();
    List<String> getPluginDependencies();
    ConfigMap getDefaultConfig();
    MessageMap getDefaultMessages();
    void initialize();
    void disable();
}
