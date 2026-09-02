package nl.hauntedmc.featureframework.cluster;

import nl.hauntedmc.featureframework.toolkit.io.config.ConfigMutationDeniedException;

import java.nio.file.Path;

/** Denial for a managed replicated path on a follower. */
public final class ReplicaManagedConfigurationException extends ConfigMutationDeniedException {
    private static final long serialVersionUID = 1L;

    private final String namespace;
    private final String applicationId;
    private final String groupId;
    private final String configuredLeader;

    public ReplicaManagedConfigurationException(
            Path relativePath,
            String operation,
            ReplicaGroupIdentity group
    ) {
        super(relativePath, operation,
                "Configuration '" + relativePath + "' is managed by replica group " + group.groupId()
                        + "; modify configured leader " + group.configuredLeader() + " instead.");
        namespace = group.namespace();
        applicationId = group.applicationId();
        groupId = group.groupId();
        configuredLeader = group.configuredLeader();
    }

    public ReplicaGroupIdentity group() {
        return new ReplicaGroupIdentity(namespace, applicationId, groupId, configuredLeader);
    }

    public String configuredLeader() { return configuredLeader; }
}