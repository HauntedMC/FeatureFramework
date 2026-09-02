package nl.hauntedmc.featureframework.cluster;

import java.util.Optional;
import java.util.concurrent.CompletionStage;

/** Durable source of immutable configuration generations. */
public interface ReplicaGenerationRepository {
    CompletionStage<Optional<ConfigGeneration>> loadActive(ReplicaGroupIdentity group);

    CompletionStage<Optional<ConfigGeneration>> loadGeneration(ReplicaGroupIdentity group, long generation);

    CompletionStage<ConfigGeneration> publish(
            ReplicaGroupIdentity group,
            ConfigGeneration candidate,
            long fencingToken
    );

    CompletionStage<Void> recordNodeState(
            ReplicaGroupIdentity group,
            ReplicaNodeIdentity node,
            long appliedGeneration,
            ReplicaStatus.State state,
            String detail
    );
}
