package nl.hauntedmc.featureframework.cluster.dataprovider;

import nl.hauntedmc.dataprovider.database.coordination.CoordinationDataAccess;
import nl.hauntedmc.dataprovider.database.coordination.FencedLease;
import nl.hauntedmc.featureframework.cluster.ReplicaAuthority;
import nl.hauntedmc.featureframework.cluster.ReplicaGroupIdentity;
import nl.hauntedmc.featureframework.cluster.ReplicaLeaseCoordinator;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

/** DataProvider Redis adapter for FeatureFramework replica authority. */
public final class DataProviderReplicaLeaseCoordinator implements ReplicaLeaseCoordinator {
    private final CoordinationDataAccess coordination;

    public DataProviderReplicaLeaseCoordinator(CoordinationDataAccess coordination) {
        this.coordination = Objects.requireNonNull(coordination, "coordination");
    }

    @Override
    public CompletionStage<Optional<ReplicaAuthority>> acquire(
            ReplicaGroupIdentity group,
            String owner,
            Duration ttl
    ) {
        Objects.requireNonNull(group, "group");
        return coordination.acquire(group.authorityResource(), owner, ttl)
                .thenApply(result -> result.map(DataProviderReplicaLeaseCoordinator::adapt));
    }

    @Override
    public CompletionStage<Optional<ReplicaAuthority>> renew(ReplicaAuthority authority, Duration ttl) {
        Objects.requireNonNull(authority, "authority");
        FencedLease lease = new FencedLease(
                authority.resource(), authority.owner(), authority.fencingToken(), authority.expiresAt());
        return coordination.renew(lease, ttl)
                .thenApply(result -> result.map(DataProviderReplicaLeaseCoordinator::adapt));
    }

    @Override
    public CompletionStage<Boolean> release(ReplicaAuthority authority) {
        Objects.requireNonNull(authority, "authority");
        return coordination.release(new FencedLease(
                authority.resource(), authority.owner(), authority.fencingToken(), authority.expiresAt()));
    }

    private static ReplicaAuthority adapt(FencedLease lease) {
        return new ReplicaAuthority(lease.resource(), lease.owner(), lease.fencingToken(), lease.expiresAt());
    }
}
