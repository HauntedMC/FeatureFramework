package nl.hauntedmc.featureframework.api.feature;

/** Declares where a feature is eligible to run within a replica group. */
public enum FeaturePlacement {
    /** The feature runs independently on every node. */
    ALL_NODES,

    /** The feature runs only while this node holds configured group-leader authority. */
    GROUP_LEADER_ONLY
}
