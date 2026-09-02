package nl.hauntedmc.featureframework.cluster;

import java.time.Instant;
import java.util.Objects;

/** Last successfully proven fenced authority for the configured leader. */
public record ReplicaAuthority(
        String resource,
        String owner,
        long fencingToken,
        Instant expiresAt
) {
    public ReplicaAuthority {
        resource = text(resource, "resource");
        owner = text(owner, "owner");
        if (fencingToken <= 0) throw new IllegalArgumentException("fencingToken must be positive");
        expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
    }

    private static String text(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " must not be blank");
        return normalized;
    }
}
