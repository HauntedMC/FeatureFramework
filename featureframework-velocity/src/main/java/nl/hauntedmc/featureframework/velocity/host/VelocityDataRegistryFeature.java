package nl.hauntedmc.featureframework.velocity.host;

import com.velocitypowered.api.proxy.Player;
import nl.hauntedmc.dataregistry.api.DataRegistryApi;
import nl.hauntedmc.featureframework.integration.dataregistry.PlayerReferenceResolver;
import nl.hauntedmc.featureframework.velocity.integration.dataregistry.VelocityDataRegistryIdentityGate;

import java.util.Optional;
import java.util.UUID;

/** Velocity feature base that supplies the framework DataRegistry identity-readiness context. */
public abstract class VelocityDataRegistryFeature<P, D> extends VelocityFeature<P, D>
        implements VelocityDataRegistryIdentityGate.Context {
    private volatile PlayerReferenceResolver playerReferences;

    protected VelocityDataRegistryFeature(VelocityFeatureContext<P, D> context) {
        super(context);
    }

    @Override
    public DataRegistryApi dataRegistry() {
        return DataRegistryApi.class.cast(getContext().dataRegistryService());
    }

    /** Shared resolver for immutable player references within this feature generation. */
    public PlayerReferenceResolver playerReferences() {
        PlayerReferenceResolver current = playerReferences;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            current = playerReferences;
            if (current == null) {
                current = new PlayerReferenceResolver(dataRegistry());
                playerReferences = current;
            }
            return current;
        }
    }

    @Override
    public void scheduleContinuation(Runnable continuation) {
        getLifecycleManager().getTaskManager().scheduleTask(continuation);
    }

    @Override
    public Optional<Player> connectedPlayer(UUID playerId) {
        return getContext().proxy().getPlayer(playerId);
    }

    @Override
    public void warn(String message) {
        getLogger().warn(message);
    }
}
