package nl.hauntedmc.featureframework.feature;

import nl.hauntedmc.featureframework.toolkit.io.config.ConfigMap;
import nl.hauntedmc.featureframework.toolkit.io.localization.MessageMap;

import java.util.List;

/** Platform-neutral contract implemented by every managed feature. */
public interface Feature {
    String name();
    String version();
    List<String> dependencies();
    List<String> pluginDependencies();
    ConfigMap defaultConfig();
    MessageMap defaultMessages();
    void initialize();
    void disable();
}
