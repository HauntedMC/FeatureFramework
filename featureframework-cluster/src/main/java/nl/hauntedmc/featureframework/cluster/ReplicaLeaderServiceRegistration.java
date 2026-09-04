package nl.hauntedmc.featureframework.cluster;

/** Handle for a {@link ReplicaLeaderService} registration. */
@FunctionalInterface
public interface ReplicaLeaderServiceRegistration extends AutoCloseable {
    /** Stops the service when necessary and prevents all future controller-driven starts. */
    @Override
    void close();
}
