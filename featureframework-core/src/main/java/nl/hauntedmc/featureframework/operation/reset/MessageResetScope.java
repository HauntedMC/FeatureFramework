package nl.hauntedmc.featureframework.operation.reset;

/** Determines whether a localization reset also removes per-language override files. */
public enum MessageResetScope {
    MAIN_ONLY,
    MAIN_AND_OVERRIDES
}
