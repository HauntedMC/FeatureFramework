package nl.hauntedmc.featureframework.velocity.integration.dataregistry;

import com.velocitypowered.api.proxy.Player;
import nl.hauntedmc.dataregistry.api.DataRegistryApi;
import nl.hauntedmc.dataregistry.api.session.NetworkSession;
import nl.hauntedmc.dataregistry.api.session.SessionFence;
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

    /**
     * Waits for DataRegistry identity readiness without allowing work from an obsolete Velocity connection to run
     * against a replacement connection for the same UUID.
     *
     * <p>The originating {@link Player} object is always treated as a connection fence. When DataRegistry already has
     * a live network session for that connection, its immutable {@link SessionFence} is captured as an additional
     * authority fence. Both are revalidated on the owned scheduler immediately before the action executes.</p>
     */
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
        Optional<SessionFence> expectedSession = captureSessionFence(context, playerId);

        AsyncReadinessGate.runWhenReady(
                () -> context.dataRegistry().players().whenReady(playerId),
                context::scheduleContinuation,
                ignored -> {
                    if (!isOriginatingConnectionCurrent(context, playerId, player)) {
                        return;
                    }
                    if (expectedSession.isPresent()
                            && !isExpectedSessionCurrent(context, playerId, expectedSession.orElseThrow(), operationName)) {
                        return;
                    }
                    action.accept(player);
                },
                message -> context.warn("DataRegistryApi " + message),
                operationName
        );
    }

    private static Optional<SessionFence> captureSessionFence(Context context, UUID playerId) {
        try {
            return context.dataRegistry().sessions().cached(playerId).map(NetworkSession::fence);
        } catch (RuntimeException ignored) {
            // Exact Velocity connection identity remains a valid fence when session state is not yet published.
            return Optional.empty();
        }
    }

    private static boolean isOriginatingConnectionCurrent(Context context, UUID playerId, Player expectedPlayer) {
        try {
            return context.connectedPlayer(playerId).orElse(null) == expectedPlayer;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static boolean isExpectedSessionCurrent(
            Context context,
            UUID playerId,
            SessionFence expectedSession,
            String operationName
    ) {
        try {
            return context.dataRegistry().sessions().cached(playerId)
                    .map(session -> session.matches(expectedSession))
                    .orElse(false);
        } catch (RuntimeException failure) {
            context.warn("DataRegistryApi session validation was unavailable for " + operationName + ": "
                    + AsyncReadinessGate.rootMessage(failure));
            return false;
        }
    }
}
