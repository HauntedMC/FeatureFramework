package nl.hauntedmc.featureframework.localization;

import nl.hauntedmc.featureframework.toolkit.io.localization.MessageMap;

/** Minimal localization lifecycle required by a managed feature context. */
public interface FeatureLocalization {
    void registerDefaultMessages(MessageMap messages);
    void reloadLocalization();

    /**
     * Reloads localization for managed startup without emitting an operator-facing INFO message.
     *
     * <p>Custom implementations retain the legacy behavior by default. Framework implementations can override
     * this to keep routine startup diagnostics at DEBUG while explicit administrator reloads remain visible.</p>
     */
    default void reloadLocalizationQuietly() {
        reloadLocalization();
    }
}
