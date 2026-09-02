package nl.hauntedmc.featureframework.cluster;

import java.time.Instant;
import java.util.Objects;

/** Last successfully proven fenced authority for the configured leader. */
public record ReplicaAuthority(
        String owner,
        long fencingToken,
        Instant expiresAt
) {
    public ReplicaAuthority {
        owner = Objects.requireNonNull(owner, "owner").trim();
        if (owner.isEmpty()) throw new IllegalArgumentException("owner must not be blank");
        if (fencingToken <= 0) throw new IllegalArgumentException("fencingToken must be positive");
        expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
    }
}
