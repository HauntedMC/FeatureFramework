package nl.hauntedmc.featureframework.velocity.integration.dataregistry;

import com.velocitypowered.api.proxy.Player;
import nl.hauntedmc.dataregistry.api.DataRegistryApi;
import nl.hauntedmc.featureframework.integration.dataregistry.DataRegistryResources;
import nl.hauntedmc.featureframework.integration.dataregistry.PlayerReferenceResolver;
import nl.hauntedmc.featureframework.velocity.host.VelocityFeatureContext;
import nl.hauntedmc.featureframework.velocity.lifecycle.VelocityFeatureResources;
import nl.hauntedmc.featureframework.velocity.log.FeatureLogger;

import java.util.Optional;
import java.util.UUID;

/** Reusable DataRegistry access contract for Velocity features without imposing a specialized base class. */
public interface VelocityDataRegistryAccess extends VelocityDataRegistryIdentityGate.Context {
    VelocityFeatureResources resources();
    VelocityFeatureContext<?> context();
    FeatureLogger logger();

    @Override default DataRegistryApi dataRegistry() {
        return resources().extensions().require(DataRegistryResources.KEY).registry();
    }

    default PlayerReferenceResolver playerReferences() {
        return resources().extensions().require(DataRegistryResources.KEY).players();
    }

    @Override default void scheduleContinuation(Runnable continuation) {
        resources().tasks().scheduleTask(continuation);
    }

    @Override default Optional<Player> connectedPlayer(UUID playerId) {
        return context().proxy().getPlayer(playerId);
    }

    @Override default void warn(String message) { logger().warn(message); }
}
