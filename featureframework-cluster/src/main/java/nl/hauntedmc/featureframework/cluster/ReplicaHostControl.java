package nl.hauntedmc.featureframework.cluster;

import nl.hauntedmc.featureframework.api.feature.FeatureActivationPolicy;
import nl.hauntedmc.featureframework.toolkit.io.config.ConfigMutationPolicy;

/** Minimal host hook used by replica orchestration without creating another bootstrap abstraction. */
public interface ReplicaHostControl {
    void installReplicaPolicies(FeatureActivationPolicy activationPolicy, ConfigMutationPolicy mutationPolicy);
    boolean reconcileReplicaGraph();
}
