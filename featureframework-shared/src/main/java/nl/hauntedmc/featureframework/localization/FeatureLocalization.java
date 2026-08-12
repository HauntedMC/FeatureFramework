package nl.hauntedmc.featureframework.localization;

import nl.hauntedmc.featureframework.toolkit.io.localization.MessageMap;

/** Minimal localization lifecycle required by a managed feature context. */
public interface FeatureLocalization {
    void registerDefaultMessages(MessageMap messages);
    void reloadLocalization();
}
