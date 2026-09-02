package nl.hauntedmc.featureframework.cluster;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

/** Backend-neutral fenced authority operations for one configured replica group. */
public interface ReplicaLeaseCoordinator {
    CompletionStage<Optional<ReplicaAuthority>> acquire(
            ReplicaGroupIdentity group,
            String owner,
            Duration ttl
    );

    CompletionStage<Optional<ReplicaAuthority>> renew(ReplicaAuthority authority, Duration ttl);

    CompletionStage<Boolean> release(ReplicaAuthority authority);
}
