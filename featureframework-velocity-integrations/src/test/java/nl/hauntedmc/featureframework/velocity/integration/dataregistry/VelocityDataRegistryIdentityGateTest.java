package nl.hauntedmc.featureframework.velocity.integration.dataregistry;

import com.velocitypowered.api.proxy.Player;
import nl.hauntedmc.dataregistry.api.DataRegistryApi;
import nl.hauntedmc.dataregistry.api.player.PlayerData;
import nl.hauntedmc.dataregistry.api.player.PlayerIdentity;
import nl.hauntedmc.dataregistry.api.session.NetworkSession;
import nl.hauntedmc.dataregistry.api.session.NetworkSessionApi;
import nl.hauntedmc.dataregistry.api.session.SessionFence;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VelocityDataRegistryIdentityGateTest {

    @Test
    void executesForSameConnectionAndSession() {
        Fixture fixture = fixtureWithSession();
        AtomicReference<Player> executed = new AtomicReference<>();

        VelocityDataRegistryIdentityGate.runWhenReady(
                fixture.context,
                fixture.originatingPlayer,
                executed::set,
                "test operation"
        );
        fixture.completeReadiness();
        fixture.runScheduled();

        assertSame(fixture.originatingPlayer, executed.get());
    }

    @Test
    void dropsCallbackAfterOriginatingConnectionDisconnects() {
        Fixture fixture = fixtureWithSession();
        AtomicInteger executions = new AtomicInteger();

        VelocityDataRegistryIdentityGate.runWhenReady(
                fixture.context,
                fixture.originatingPlayer,
                ignored -> executions.incrementAndGet(),
                "test operation"
        );
        fixture.currentPlayer.set(null);
        fixture.completeReadiness();
        fixture.runScheduled();

        assertEquals(0, executions.get());
    }

    @Test
    void dropsCallbackWhenReplacementConnectionUsesSameUuid() {
        Fixture fixture = fixtureWithSession();
        Player replacement = mock(Player.class);
        when(replacement.getUniqueId()).thenReturn(fixture.playerId);
        AtomicInteger executions = new AtomicInteger();

        VelocityDataRegistryIdentityGate.runWhenReady(
                fixture.context,
                fixture.originatingPlayer,
                ignored -> executions.incrementAndGet(),
                "test operation"
        );
        fixture.completeReadiness();
        fixture.currentPlayer.set(replacement);
        fixture.runScheduled();

        assertEquals(0, executions.get());
    }

    @Test
    void dropsReplacementConnectionWhenSessionWasNotPublishedYet() {
        Fixture fixture = fixtureWithoutSession();
        Player replacement = mock(Player.class);
        when(replacement.getUniqueId()).thenReturn(fixture.playerId);
        AtomicInteger executions = new AtomicInteger();

        VelocityDataRegistryIdentityGate.runWhenReady(
                fixture.context,
                fixture.originatingPlayer,
                ignored -> executions.incrementAndGet(),
                "test operation"
        );
        fixture.completeReadiness();
        fixture.currentPlayer.set(replacement);
        fixture.runScheduled();

        assertEquals(0, executions.get());
    }

    @Test
    void dropsCallbackWhenDataRegistrySessionFenceChanges() {
        Fixture fixture = fixtureWithSession();
        SessionFence replacementFence = new SessionFence(
                "proxy-a",
                UUID.randomUUID(),
                UUID.randomUUID(),
                2L,
                2L
        );
        NetworkSession replacementSession = mock(NetworkSession.class);
        when(replacementSession.matches(fixture.sessionFence)).thenReturn(false);
        when(replacementSession.fence()).thenReturn(replacementFence);
        AtomicInteger sessionReads = new AtomicInteger();
        when(fixture.sessions.cached(fixture.playerId)).thenAnswer(ignored ->
                sessionReads.getAndIncrement() == 0
                        ? Optional.of(fixture.session)
                        : Optional.of(replacementSession)
        );
        AtomicInteger executions = new AtomicInteger();

        VelocityDataRegistryIdentityGate.runWhenReady(
                fixture.context,
                fixture.originatingPlayer,
                ignored -> executions.incrementAndGet(),
                "test operation"
        );
        fixture.completeReadiness();
        fixture.runScheduled();

        assertEquals(0, executions.get());
    }

    @Test
    void stillUsesExactConnectionWhenSessionIsNotPublishedYet() {
        Fixture fixture = fixtureWithoutSession();
        AtomicReference<Player> executed = new AtomicReference<>();

        VelocityDataRegistryIdentityGate.runWhenReady(
                fixture.context,
                fixture.originatingPlayer,
                executed::set,
                "test operation"
        );
        fixture.completeReadiness();
        fixture.runScheduled();

        assertSame(fixture.originatingPlayer, executed.get());
        verify(fixture.context, never()).warn(any());
    }

    private static Fixture fixtureWithSession() {
        Fixture fixture = baseFixture();
        fixture.sessionFence = new SessionFence(
                "proxy-a",
                UUID.randomUUID(),
                UUID.randomUUID(),
                1L,
                1L
        );
        fixture.session = mock(NetworkSession.class);
        when(fixture.session.fence()).thenReturn(fixture.sessionFence);
        when(fixture.session.matches(fixture.sessionFence)).thenReturn(true);
        when(fixture.sessions.cached(fixture.playerId)).thenReturn(Optional.of(fixture.session));
        return fixture;
    }

    private static Fixture fixtureWithoutSession() {
        Fixture fixture = baseFixture();
        when(fixture.sessions.cached(fixture.playerId)).thenReturn(Optional.empty());
        return fixture;
    }

    private static Fixture baseFixture() {
        Fixture fixture = new Fixture();
        fixture.playerId = UUID.randomUUID();
        fixture.originatingPlayer = mock(Player.class);
        when(fixture.originatingPlayer.getUniqueId()).thenReturn(fixture.playerId);
        fixture.currentPlayer = new AtomicReference<>(fixture.originatingPlayer);
        fixture.context = mock(VelocityDataRegistryIdentityGate.Context.class);
        fixture.registry = mock(DataRegistryApi.class);
        fixture.players = mock(PlayerData.class);
        fixture.sessions = mock(NetworkSessionApi.class);
        fixture.readiness = new CompletableFuture<>();
        fixture.scheduled = new ArrayList<>();

        when(fixture.context.dataRegistry()).thenReturn(fixture.registry);
        when(fixture.registry.players()).thenReturn(fixture.players);
        when(fixture.registry.sessions()).thenReturn(fixture.sessions);
        when(fixture.players.whenReady(fixture.playerId)).thenReturn(fixture.readiness);
        when(fixture.context.connectedPlayer(fixture.playerId))
                .thenAnswer(ignored -> Optional.ofNullable(fixture.currentPlayer.get()));
        doAnswer(invocation -> {
            fixture.scheduled.add(invocation.getArgument(0, Runnable.class));
            return null;
        }).when(fixture.context).scheduleContinuation(any(Runnable.class));
        return fixture;
    }

    private static final class Fixture {
        private UUID playerId;
        private Player originatingPlayer;
        private AtomicReference<Player> currentPlayer;
        private VelocityDataRegistryIdentityGate.Context context;
        private DataRegistryApi registry;
        private PlayerData players;
        private NetworkSessionApi sessions;
        private NetworkSession session;
        private SessionFence sessionFence;
        private CompletableFuture<Optional<PlayerIdentity>> readiness;
        private List<Runnable> scheduled;

        private void completeReadiness() {
            readiness.complete(Optional.of(mock(PlayerIdentity.class)));
            assertEquals(1, scheduled.size());
        }

        private void runScheduled() {
            scheduled.remove(0).run();
        }
    }
}
