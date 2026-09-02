package nl.hauntedmc.featureframework.cluster;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/** Point-in-time control-plane status for one replica process. */
public record ReplicaStatus(
        ReplicaRole role,
        State state,
        Optional<ReplicaAuthority> authority,
        OptionalLong appliedGeneration,
        Optional<String> detail
) {
    public enum State {
        STANDALONE,
        BOOTSTRAPPING,
        READY,
        OUT_OF_SYNC,
        DRIFTED,
        UNAVAILABLE
    }

    public ReplicaStatus {
        role = Objects.requireNonNull(role, "role");
        state = Objects.requireNonNull(state, "state");
        authority = authority == null ? Optional.empty() : authority;
        appliedGeneration = appliedGeneration == null ? OptionalLong.empty() : appliedGeneration;
        detail = detail == null ? Optional.empty() : detail.filter(value -> !value.isBlank());
    }
}
