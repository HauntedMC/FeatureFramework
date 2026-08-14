package nl.hauntedmc.featureframework.integration.dataregistry;

import nl.hauntedmc.dataregistry.api.player.PlayerDirectory;
import nl.hauntedmc.dataregistry.api.player.PlayerIdentity;
import nl.hauntedmc.dataregistry.api.player.PlayerLookup;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlayerReferenceResolverTest {

    @Test
    void resolveReferenceUsesActiveIdentityWithoutQueryingPersistence() {
        PlayerDirectory directory = mock(PlayerDirectory.class);
        UUID uuid = UUID.randomUUID();
        PlayerIdentity identity = new PlayerIdentity(12L, uuid, "Alice");

        when(directory.findActiveIdentityCached(uuid)).thenReturn(Optional.of(identity));

        PlayerReference result = new PlayerReferenceResolver(directory).resolveReference(uuid);

        assertEquals(PlayerReference.from(identity), result);
        verify(directory, never()).findByUuid(uuid);
    }

    @Test
    void resolveReferenceUsesPersistedIdentityFromBackgroundWorkerWhenPlayerIsOffline() throws Exception {
        PlayerDirectory directory = mock(PlayerDirectory.class);
        UUID uuid = UUID.randomUUID();
        PlayerIdentity identity = new PlayerIdentity(13L, uuid, "OfflineAlice");

        when(directory.findActiveIdentityCached(uuid)).thenReturn(Optional.empty());
        when(directory.findByUuid(uuid)).thenReturn(CompletableFuture.completedFuture(Optional.of(identity)));

        PlayerReferenceResolver resolver = new PlayerReferenceResolver(directory);
        PlayerReference result = runOnThread("application-vote-worker", () -> resolver.resolveReference(uuid));

        assertEquals(PlayerReference.from(identity), result);
        verify(directory).findByUuid(uuid);
    }

    @Test
    void synchronousLookupNeverQueriesPersistenceFromEventThread() throws Exception {
        PlayerDirectory directory = mock(PlayerDirectory.class);
        UUID uuid = UUID.randomUUID();

        when(directory.snapshotActiveIdentities()).thenReturn(Map.of());

        PlayerReferenceResolver resolver = new PlayerReferenceResolver(directory);
        Optional<PlayerReference> result = runOnThread(
                "Velocity Netty EventLoop",
                () -> resolver.findByIdentifier("OfflineAlice")
        );

        assertEquals(Optional.empty(), result);
        verify(directory, never()).findByIdentifier("OfflineAlice");
        verify(directory, never()).findByUuid(uuid);
    }

    @Test
    void asyncLookupQueriesPersistenceWithoutBlockingEventThread() throws Exception {
        PlayerDirectory directory = mock(PlayerDirectory.class);
        UUID uuid = UUID.randomUUID();
        PlayerIdentity identity = new PlayerIdentity(14L, uuid, "OfflineAlice");

        when(directory.snapshotActiveIdentities()).thenReturn(Map.of());
        when(directory.findByIdentifier("OfflineAlice"))
                .thenReturn(CompletableFuture.completedFuture(Optional.of(identity)));

        PlayerReferenceResolver resolver = new PlayerReferenceResolver(directory);
        Optional<PlayerReference> result = runOnThread(
                "Velocity Netty EventLoop",
                () -> resolver.findByIdentifierAsync("OfflineAlice").toCompletableFuture().join()
        );

        assertEquals(Optional.of(PlayerReference.from(identity)), result);
        verify(directory).findByIdentifier("OfflineAlice");
    }

    @Test
    void resolveReferenceReturnsNullWhenPersistedIdentityDoesNotExist() throws Exception {
        PlayerDirectory directory = mock(PlayerDirectory.class);
        UUID uuid = UUID.randomUUID();

        when(directory.findActiveIdentityCached(uuid)).thenReturn(Optional.empty());
        when(directory.findByUuid(uuid)).thenReturn(CompletableFuture.completedFuture(Optional.empty()));

        PlayerReferenceResolver resolver = new PlayerReferenceResolver(directory);
        PlayerReference result = runOnThread("application-worker", () -> resolver.resolveReference(uuid));

        assertNull(result);
        verify(directory).findByUuid(uuid);
    }

    @Test
    void findByIdentifierAsyncUsesPersistedCaseInsensitiveUsernameWhenOffline() {
        PlayerDirectory directory = mock(PlayerDirectory.class);
        UUID uuid = UUID.randomUUID();
        PlayerIdentity identity = new PlayerIdentity(21L, uuid, "Alice");

        when(directory.snapshotActiveIdentities()).thenReturn(Map.of());
        when(directory.findByIdentifier("aLiCe"))
                .thenReturn(CompletableFuture.completedFuture(Optional.of(identity)));

        Optional<PlayerReference> result = new PlayerReferenceResolver(directory)
                .findByIdentifierAsync("aLiCe")
                .toCompletableFuture()
                .join();

        assertEquals(Optional.of(PlayerReference.from(identity)), result);
        verify(directory).findByIdentifier("aLiCe");
    }

    @Test
    void findByIdsAsyncCombinesActiveAndPersistedPlayersInRequestedOrder() {
        PlayerDirectory directory = mock(PlayerDirectory.class);
        UUID activeUuid = UUID.randomUUID();
        UUID offlineUuid = UUID.randomUUID();
        PlayerIdentity active = new PlayerIdentity(31L, activeUuid, "ActiveAlice");
        PlayerIdentity offline = new PlayerIdentity(32L, offlineUuid, "OfflineBob");
        PlayerLookup offlineLookup = PlayerLookup.playerId(32L);
        PlayerLookup missingLookup = PlayerLookup.playerId(33L);

        when(directory.snapshotActiveIdentities()).thenReturn(Map.of(activeUuid.toString(), active));
        when(directory.findIdentities(List.of(offlineLookup, missingLookup))).thenReturn(
                CompletableFuture.completedFuture(Map.of(
                        offlineLookup, Optional.of(offline),
                        missingLookup, Optional.empty()
                ))
        );

        List<PlayerReference> result = new PlayerReferenceResolver(directory)
                .findByIdsAsync(List.of(32L, 31L, 33L, 32L, -1L))
                .toCompletableFuture()
                .join();

        assertEquals(List.of(PlayerReference.from(offline), PlayerReference.from(active)), result);
        verify(directory).findIdentities(List.of(offlineLookup, missingLookup));
    }

    @Test
    void resolveReferenceByIdUsesActiveThenPersistedIdentityAndFinallyScalarId() throws Exception {
        PlayerDirectory directory = mock(PlayerDirectory.class);
        UUID activeUuid = UUID.randomUUID();
        UUID offlineUuid = UUID.randomUUID();
        PlayerIdentity activeIdentity = new PlayerIdentity(22L, activeUuid, "Bob");
        PlayerIdentity offlineIdentity = new PlayerIdentity(23L, offlineUuid, "OfflineBob");

        when(directory.snapshotActiveIdentities())
                .thenReturn(Map.of(activeUuid.toString(), activeIdentity))
                .thenReturn(Map.of())
                .thenReturn(Map.of());
        when(directory.findByPlayerId(23L))
                .thenReturn(CompletableFuture.completedFuture(Optional.of(offlineIdentity)));
        when(directory.findByPlayerId(24L))
                .thenReturn(CompletableFuture.completedFuture(Optional.empty()));

        PlayerReferenceResolver resolver = new PlayerReferenceResolver(directory);

        assertEquals(PlayerReference.from(activeIdentity), resolver.resolveReferenceById(22L));
        assertEquals(
                PlayerReference.from(offlineIdentity),
                runOnThread("application-worker", () -> resolver.resolveReferenceById(23L))
        );
        assertEquals(
                PlayerReference.byId(24L),
                runOnThread("application-worker", () -> resolver.resolveReferenceById(24L))
        );
    }

    @Test
    void detectsLikelyServerAndEventThreads() {
        assertTrue(PlayerReferenceResolver.isLikelyServerEventThread("Server thread"));
        assertTrue(PlayerReferenceResolver.isLikelyServerEventThread("Velocity Netty EventLoop"));
        assertTrue(PlayerReferenceResolver.isLikelyServerEventThread("main"));
        assertFalse(PlayerReferenceResolver.isLikelyServerEventThread("application-vote-worker"));
        assertFalse(PlayerReferenceResolver.isLikelyServerEventThread(null));
    }

    private static <T> T runOnThread(String name, Supplier<T> supplier) throws Exception {
        CompletableFuture<T> result = new CompletableFuture<>();
        Thread thread = new Thread(() -> {
            try {
                result.complete(supplier.get());
            } catch (Throwable throwable) {
                result.completeExceptionally(throwable);
            }
        }, name);
        thread.start();
        return result.get(5, TimeUnit.SECONDS);
    }
}
