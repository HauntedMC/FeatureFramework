package nl.hauntedmc.featureframework.paper.integration.dataregistry;

import nl.hauntedmc.dataregistry.api.DataRegistryApi;
import nl.hauntedmc.dataregistry.api.player.PlayerIdentity;
import nl.hauntedmc.featureframework.integration.dataregistry.PlayerIdentityResolver;
import nl.hauntedmc.featureframework.lifecycle.AsyncReadinessGate;
import org.bukkit.entity.Player;

import java.util.Objects;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Bridges asynchronous DataRegistry identity readiness back onto a Paper feature's main-thread scheduler. */
public final class PaperDataRegistryIdentityGate {
    private PaperDataRegistryIdentityGate() {
    }

    public interface Context {
        DataRegistryApi dataRegistry();
        void scheduleContinuation(Runnable continuation);
        boolean hostAvailable();
        void warn(String message);
    }

    public static void runWhenReady(
            Context context,
            Player player,
            Consumer<Player> action,
            String operationName
    ) {
        Objects.requireNonNull(action, "action");
        runWhenReady(context, player, (readyPlayer, identity) -> {
            if (identity.playerId() > 0L) action.accept(readyPlayer);
        }, operationName);
    }

    public static void runWhenReady(
            Context context,
            Player player,
            BiConsumer<Player, PlayerIdentity> action,
            String operationName
    ) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(operationName, "operationName");
        UUID playerId = player.getUniqueId();
        PlayerIdentityResolver resolver = new PlayerIdentityResolver(context.dataRegistry());

        AsyncReadinessGate.runWhenReady(
                () -> resolver.whenReady(playerId),
                context::scheduleContinuation,
                identity -> {
                    if (context.hostAvailable() && player.isOnline() && playerId.equals(player.getUniqueId())) {
                        action.accept(player, identity);
                    }
                },
                message -> context.warn("DataRegistry " + message),
                operationName
        );
    }
}
