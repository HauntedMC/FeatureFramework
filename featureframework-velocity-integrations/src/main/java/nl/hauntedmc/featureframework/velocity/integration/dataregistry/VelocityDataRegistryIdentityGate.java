package nl.hauntedmc.featureframework.velocity.integration.dataregistry;

import com.velocitypowered.api.proxy.Player;
import nl.hauntedmc.dataregistry.api.DataRegistryApi;
import nl.hauntedmc.featureframework.lifecycle.AsyncReadinessGate;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/** Bridges asynchronous DataRegistry identity readiness back onto a Velocity feature scheduler. */
public final class VelocityDataRegistryIdentityGate {
    private VelocityDataRegistryIdentityGate() {
    }

    public interface Context {
        DataRegistryApi dataRegistry();
        void scheduleContinuation(Runnable continuation);
        Optional<Player> connectedPlayer(UUID playerId);
        void warn(String message);
    }

    public static void runWhenReady(
            Context context,
            Player player,
            Consumer<Player> action,
            String operationName
    ) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(operationName, "operationName");
        UUID playerId = player.getUniqueId();

        AsyncReadinessGate.runWhenReady(
                () -> context.dataRegistry().players().whenReady(playerId),
                context::scheduleContinuation,
                ignored -> context.connectedPlayer(playerId).ifPresent(action),
                message -> context.warn("DataRegistryApi " + message),
                operationName
        );
    }

}
