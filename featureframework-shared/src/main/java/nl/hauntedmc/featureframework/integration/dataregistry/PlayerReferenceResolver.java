package nl.hauntedmc.featureframework.integration.dataregistry;

import nl.hauntedmc.dataregistry.api.DataRegistryApi;
import nl.hauntedmc.dataregistry.api.player.PlayerDirectory;
import nl.hauntedmc.dataregistry.api.player.PlayerIdentity;
import nl.hauntedmc.dataregistry.api.player.PlayerLookup;
import nl.hauntedmc.featureframework.persistence.BoundedPersistenceLookup;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

/**
 * Resolves immutable DataRegistry player snapshots without importing or managing DataRegistry entities.
 *
 * <p>Synchronous methods are cache-first and may use a bounded persisted fallback only from
 * background threads. They never wait for persistence on a likely server, event-loop, or Netty
 * thread. Callers that require offline-player correctness should use the asynchronous methods.</p>
 */
public final class PlayerReferenceResolver {

    private static final long PERSISTED_LOOKUP_TIMEOUT_SECONDS = 3L;

    private final PlayerDirectory playerDirectory;

    public PlayerReferenceResolver(DataRegistryApi dataRegistry) {
        this(Objects.requireNonNull(dataRegistry, "dataRegistry").players().identities());
    }

    public PlayerReferenceResolver(PlayerDirectory playerDirectory) {
        this.playerDirectory = Objects.requireNonNull(playerDirectory, "playerDirectory");
    }

    public Optional<PlayerReference> findByUuid(UUID uuid) {
        return findIdentityByUuid(uuid).map(PlayerReference::from);
    }

    public Optional<PlayerReference> findByUuid(String uuid) {
        return findIdentityByUuid(uuid).map(PlayerReference::from);
    }

    public Optional<PlayerReference> findByIdentifier(String identifier) {
        return findIdentityByIdentifier(identifier).map(PlayerReference::from);
    }

    public CompletionStage<Optional<PlayerReference>> findByUuidAsync(UUID uuid) {
        return findIdentityByUuidAsync(uuid).thenApply(identity -> identity.map(PlayerReference::from));
    }

    public CompletionStage<Optional<PlayerReference>> findByUuidAsync(String uuid) {
        return findIdentityByUuidAsync(uuid).thenApply(identity -> identity.map(PlayerReference::from));
    }

    public CompletionStage<Optional<PlayerReference>> findByIdentifierAsync(String identifier) {
        return findIdentityByIdentifierAsync(identifier).thenApply(identity -> identity.map(PlayerReference::from));
    }

    public CompletionStage<List<PlayerReference>> findByIdsAsync(Collection<Long> playerIds) {
        if (playerIds == null || playerIds.isEmpty()) {
            return CompletableFuture.completedFuture(List.of());
        }

        LinkedHashSet<Long> requestedIds = new LinkedHashSet<>();
        for (Long playerId : playerIds) {
            if (playerId != null && playerId > 0L) {
                requestedIds.add(playerId);
            }
        }
        if (requestedIds.isEmpty()) {
            return CompletableFuture.completedFuture(List.of());
        }

        Map<Long, PlayerIdentity> identitiesById = new LinkedHashMap<>();
        playerDirectory.snapshotActiveIdentities().values().stream()
                .filter(identity -> requestedIds.contains(identity.playerId()))
                .forEach(identity -> identitiesById.put(identity.playerId(), identity));

        List<PlayerLookup> missingLookups = requestedIds.stream()
                .filter(playerId -> !identitiesById.containsKey(playerId))
                .map(PlayerLookup::playerId)
                .toList();
        if (missingLookups.isEmpty()) {
            return CompletableFuture.completedFuture(toReferences(requestedIds, identitiesById));
        }

        return playerDirectory.findIdentities(missingLookups).thenApply(found -> {
            if (found != null) {
                missingLookups.forEach(lookup -> {
                    Optional<PlayerIdentity> identity = found.get(lookup);
                    if (identity != null) {
                        identity.ifPresent(value -> identitiesById.put(value.playerId(), value));
                    }
                });
            }
            return toReferences(requestedIds, identitiesById);
        });
    }

    public Optional<PlayerIdentity> findIdentityByUuid(UUID uuid) {
        if (uuid == null) {
            return Optional.empty();
        }
        Optional<PlayerIdentity> cached = playerDirectory.findActiveIdentityCached(uuid);
        if (cached.isPresent() || !mayWaitForPersistence()) {
            return cached;
        }
        return await(playerDirectory.findByUuid(uuid));
    }

    public Optional<PlayerIdentity> findIdentityByUuid(String uuid) {
        if (uuid == null || uuid.isBlank()) {
            return Optional.empty();
        }
        String normalized = uuid.trim();
        Optional<PlayerIdentity> cached = playerDirectory.findActiveIdentityCached(normalized);
        if (cached.isPresent() || !mayWaitForPersistence()) {
            return cached;
        }
        return await(playerDirectory.findByUuid(normalized));
    }

    public CompletionStage<Optional<PlayerIdentity>> findIdentityByUuidAsync(UUID uuid) {
        if (uuid == null) {
            return completedEmpty();
        }
        Optional<PlayerIdentity> cached = playerDirectory.findActiveIdentityCached(uuid);
        return cached.isPresent()
                ? CompletableFuture.completedFuture(cached)
                : playerDirectory.findByUuid(uuid);
    }

    public CompletionStage<Optional<PlayerIdentity>> findIdentityByUuidAsync(String uuid) {
        if (uuid == null || uuid.isBlank()) {
            return completedEmpty();
        }
        String normalized = uuid.trim();
        Optional<PlayerIdentity> cached = playerDirectory.findActiveIdentityCached(normalized);
        return cached.isPresent()
                ? CompletableFuture.completedFuture(cached)
                : playerDirectory.findByUuid(normalized);
    }

    public Optional<PlayerIdentity> findIdentityByUsername(String username) {
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }
        String normalized = username.trim();
        Optional<PlayerIdentity> cached = findCachedIdentityByUsername(normalized);
        if (cached.isPresent() || !mayWaitForPersistence()) {
            return cached;
        }
        return await(playerDirectory.findByUsernameIgnoreCase(normalized));
    }

    public CompletionStage<Optional<PlayerIdentity>> findIdentityByUsernameAsync(String username) {
        if (username == null || username.isBlank()) {
            return completedEmpty();
        }
        String normalized = username.trim();
        Optional<PlayerIdentity> cached = findCachedIdentityByUsername(normalized);
        return cached.isPresent()
                ? CompletableFuture.completedFuture(cached)
                : playerDirectory.findByUsernameIgnoreCase(normalized);
    }

    public Optional<PlayerIdentity> findIdentityByIdentifier(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return Optional.empty();
        }
        String normalized = identifier.trim();
        Optional<PlayerIdentity> cached = findCachedIdentityByIdentifier(normalized);
        if (cached.isPresent() || !mayWaitForPersistence()) {
            return cached;
        }
        return await(playerDirectory.findByIdentifier(normalized));
    }

    public CompletionStage<Optional<PlayerIdentity>> findIdentityByIdentifierAsync(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return completedEmpty();
        }
        String normalized = identifier.trim();
        Optional<PlayerIdentity> cached = findCachedIdentityByIdentifier(normalized);
        return cached.isPresent()
                ? CompletableFuture.completedFuture(cached)
                : playerDirectory.findByIdentifier(normalized);
    }

    /** Cache-first lookup by stable player id with a bounded background-thread fallback. */
    public Optional<PlayerIdentity> findIdentityById(Long playerId) {
        if (playerId == null || playerId <= 0L) {
            return Optional.empty();
        }
        Optional<PlayerIdentity> cached = findCachedIdentityById(playerId);
        if (cached.isPresent() || !mayWaitForPersistence()) {
            return cached;
        }
        return await(playerDirectory.findByPlayerId(playerId));
    }

    public CompletionStage<Optional<PlayerIdentity>> findIdentityByIdAsync(Long playerId) {
        if (playerId == null || playerId <= 0L) {
            return completedEmpty();
        }
        Optional<PlayerIdentity> cached = findCachedIdentityById(playerId);
        return cached.isPresent()
                ? CompletableFuture.completedFuture(cached)
                : playerDirectory.findByPlayerId(playerId);
    }

    public CompletionStage<Optional<PlayerIdentity>> findPersistedIdentityByIdentifier(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return completedEmpty();
        }
        return playerDirectory.findByIdentifier(identifier.trim());
    }

    public CompletableFuture<Optional<PlayerIdentity>> whenReady(UUID uuid) {
        return playerDirectory.whenReady(uuid);
    }

    public PlayerReference resolveReference(UUID uuid) {
        return findByUuid(uuid).orElse(null);
    }

    public PlayerReference resolveReference(String uuid) {
        return findByUuid(uuid).orElse(null);
    }

    public PlayerReference resolveReferenceById(Long playerId) {
        if (playerId == null || playerId <= 0L) {
            return null;
        }
        return findIdentityById(playerId)
                .map(PlayerReference::from)
                .orElseGet(() -> PlayerReference.byId(playerId));
    }

    private Optional<PlayerIdentity> findCachedIdentityByUsername(String username) {
        String normalized = username.toLowerCase(Locale.ROOT);
        return playerDirectory.snapshotActiveIdentities().values().stream()
                .filter(identity -> identity.username().toLowerCase(Locale.ROOT).equals(normalized))
                .findFirst();
    }

    private Optional<PlayerIdentity> findCachedIdentityByIdentifier(String identifier) {
        try {
            return playerDirectory.findActiveIdentityCached(UUID.fromString(identifier));
        } catch (IllegalArgumentException ignored) {
            return findCachedIdentityByUsername(identifier);
        }
    }

    private Optional<PlayerIdentity> findCachedIdentityById(Long playerId) {
        return playerDirectory.snapshotActiveIdentities().values().stream()
                .filter(identity -> playerId.equals(identity.playerId()))
                .findFirst();
    }

    private static List<PlayerReference> toReferences(
            Collection<Long> requestedIds,
            Map<Long, PlayerIdentity> identitiesById
    ) {
        return requestedIds.stream()
                .map(identitiesById::get)
                .filter(Objects::nonNull)
                .map(PlayerReference::from)
                .toList();
    }

    private static boolean mayWaitForPersistence() {
        return BoundedPersistenceLookup.mayWaitOnCurrentThread();
    }

    static boolean isLikelyServerEventThread(String threadName) {
        return BoundedPersistenceLookup.isLikelyEventThread(threadName);
    }

    private static <T> Optional<T> await(CompletionStage<Optional<T>> stage) {
        return BoundedPersistenceLookup.awaitOptional(
                stage, PERSISTED_LOOKUP_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private static <T> CompletionStage<Optional<T>> completedEmpty() {
        return CompletableFuture.completedFuture(Optional.empty());
    }
}
