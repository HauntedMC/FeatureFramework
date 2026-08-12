package nl.hauntedmc.featureframework.integration.dataregistry;

import nl.hauntedmc.dataregistry.api.DataRegistryApi;
import nl.hauntedmc.dataregistry.api.player.PlayerDirectory;
import nl.hauntedmc.dataregistry.api.player.PlayerIdentity;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Resolves immutable DataRegistry identities for scalar player-id feature mappings.
 *
 * <p>Cache-only methods are explicitly named. All methods that may consult persistence are
 * asynchronous and use the active cache as a fast path.</p>
 */
public final class PlayerIdentityResolver {

    private final PlayerDirectory playerDirectory;

    public PlayerIdentityResolver(DataRegistryApi dataRegistry) {
        this(Objects.requireNonNull(dataRegistry, "dataRegistry").players().identities());
    }

    public PlayerIdentityResolver(PlayerDirectory playerDirectory) {
        this.playerDirectory = Objects.requireNonNull(playerDirectory, "playerDirectory");
    }

    public CompletionStage<Optional<PlayerIdentity>> findByUuid(UUID uuid) {
        if (uuid == null) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        Optional<PlayerIdentity> cached = findActiveByUuid(uuid);
        return cached.isPresent()
                ? CompletableFuture.completedFuture(cached)
                : playerDirectory.findByUuid(uuid);
    }

    public CompletionStage<Optional<PlayerIdentity>> findByUuid(String uuid) {
        String normalized = normalize(uuid);
        if (normalized == null) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        Optional<PlayerIdentity> cached = findActiveByUuid(normalized);
        return cached.isPresent()
                ? CompletableFuture.completedFuture(cached)
                : playerDirectory.findByUuid(normalized);
    }

    public CompletionStage<Optional<PlayerIdentity>> findByUsername(String username) {
        String normalized = normalize(username);
        if (normalized == null) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        Optional<PlayerIdentity> cached = findActiveByUsername(normalized);
        return cached.isPresent()
                ? CompletableFuture.completedFuture(cached)
                : playerDirectory.findByUsernameIgnoreCase(normalized);
    }

    public CompletionStage<Optional<PlayerIdentity>> findByIdentifier(String identifier) {
        String normalized = normalize(identifier);
        if (normalized == null) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        Optional<PlayerIdentity> cached = findActiveByIdentifier(normalized);
        return cached.isPresent()
                ? CompletableFuture.completedFuture(cached)
                : playerDirectory.findByIdentifier(normalized);
    }

    /**
     * Performs a persistence lookup even when no active identity is cached.
     */
    public CompletionStage<Optional<PlayerIdentity>> findPersistedByUuid(String uuid) {
        String normalized = normalize(uuid);
        return normalized == null
                ? CompletableFuture.completedFuture(Optional.empty())
                : playerDirectory.findByUuid(normalized);
    }

    public Optional<PlayerIdentity> findActiveByUuid(UUID uuid) {
        return uuid == null ? Optional.empty() : playerDirectory.findActiveIdentityCached(uuid);
    }

    public Optional<PlayerIdentity> findActiveByUuid(String uuid) {
        String normalized = normalize(uuid);
        return normalized == null
                ? Optional.empty()
                : playerDirectory.findActiveIdentityCached(normalized);
    }

    /**
     * Looks up an active identity by its current username without performing I/O.
     */
    public Optional<PlayerIdentity> findActiveByUsername(String username) {
        String normalized = normalize(username);
        if (normalized == null) {
            return Optional.empty();
        }
        return playerDirectory.snapshotActiveIdentities().values().stream()
                .filter(identity -> normalized.equalsIgnoreCase(identity.username()))
                .findFirst();
    }

    private Optional<PlayerIdentity> findActiveByIdentifier(String identifier) {
        try {
            return findActiveByUuid(UUID.fromString(identifier));
        } catch (IllegalArgumentException ignored) {
            return findActiveByUsername(identifier);
        }
    }

    /**
     * Waits for platform identity preparation and then performs a persisted lookup when readiness
     * completes empty. DataRegistry readiness is intentionally cache/lifecycle-only, so the fallback
     * is required to distinguish a known offline player from a genuinely unknown identity.
     */
    public CompletableFuture<Optional<PlayerIdentity>> whenReady(UUID uuid) {
        if (uuid == null) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        return playerDirectory.whenReady(uuid)
                .thenCompose(identity -> identity != null && identity.isPresent()
                        ? CompletableFuture.completedFuture(identity)
                        : playerDirectory.findByUuid(uuid))
                .toCompletableFuture();
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
