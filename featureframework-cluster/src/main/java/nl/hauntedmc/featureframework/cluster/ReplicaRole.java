package nl.hauntedmc.featureframework.cluster;

/** Runtime role of this process inside its configured topology. */
public enum ReplicaRole {
    STANDALONE,
    LEADER,
    FOLLOWER
}
