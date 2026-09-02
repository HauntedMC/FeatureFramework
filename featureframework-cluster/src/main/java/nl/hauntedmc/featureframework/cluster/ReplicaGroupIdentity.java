package nl.hauntedmc.featureframework.cluster;

import java.util.Objects;

/** Stable identity of one manually configured replica group. */
public record ReplicaGroupIdentity(
        String namespace,
        String applicationId,
        String groupId,
        String configuredLeader
) {
    public ReplicaGroupIdentity {
        namespace = normalize(namespace, "namespace");
        applicationId = normalize(applicationId, "applicationId");
        groupId = normalize(groupId, "groupId");
        configuredLeader = normalize(configuredLeader, "configuredLeader");
    }

    public String authorityResource() {
        return "ff:" + namespace + ":" + applicationId + ":" + groupId;
    }

    public boolean isConfiguredLeader(ReplicaNodeIdentity node) {
        return configuredLeader.equals(Objects.requireNonNull(node, "node").nodeId());
    }

    private static String normalize(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " must not be blank");
        if (normalized.indexOf(':') >= 0) throw new IllegalArgumentException(field + " must not contain ':'");
        return normalized;
    }
}
