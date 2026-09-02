package nl.hauntedmc.featureframework.cluster;

import java.util.Objects;

/** Stable physical node identity supplied by the hosting application. */
public record ReplicaNodeIdentity(String nodeId) {
    public ReplicaNodeIdentity {
        nodeId = normalize(nodeId, "nodeId");
    }

    private static String normalize(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " must not be blank");
        return normalized;
    }
}
