package nl.hauntedmc.featureframework.cluster;

/**
 * Application-owned work that may run only while the configured replica leader holds fenced authority.
 *
 * <p>Implementations must make both operations idempotent. The controller invokes {@link #stop()} before
 * reconciling leader-only features after authority is lost.</p>
 */
public interface ReplicaLeaderService {
    /** Starts leader-owned work after host startup and fenced authority are both available. */
    void start();

    /** Stops leader-owned work when authority is withdrawn or the registration is closed. */
    void stop();
}
