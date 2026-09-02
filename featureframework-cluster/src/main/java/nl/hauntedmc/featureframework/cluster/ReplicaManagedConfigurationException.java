package nl.hauntedmc.featureframework.cluster;

import nl.hauntedmc.featureframework.toolkit.io.config.ConfigMutationDeniedException;

import java.nio.file.Path;

/** Denial for a managed replicated path on a follower. */
public final class ReplicaManagedConfigurationException extends ConfigMutationDeniedException {
    private final ReplicaGroupIdentity group;

    public ReplicaManagedConfigurationException(
            Path relativePath,
            String operation,
            ReplicaGroupIdentity group
    ) {
        super(relativePath, operation,
                "Configuration '" + relativePath + "' is managed by replica group " + group.groupId()
                        + "; modify configured leader " + group.configuredLeader() + " instead.");
        this.group = group;
    }

    public ReplicaGroupIdentity group() { return group; }
    public String configuredLeader() { return group.configuredLeader(); }
}
