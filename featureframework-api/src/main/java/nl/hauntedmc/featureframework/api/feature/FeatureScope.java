package nl.hauntedmc.featureframework.api.feature;

/** The widest observable boundary of a feature. */
public enum FeatureScope {
    /** All effects are confined to the current plugin process. */
    NODE,
    /** At least one capability or effect spans network nodes. */
    NETWORK
}
