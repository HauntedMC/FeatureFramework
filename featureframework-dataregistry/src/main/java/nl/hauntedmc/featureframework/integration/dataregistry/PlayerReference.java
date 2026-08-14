package nl.hauntedmc.featureframework.integration.dataregistry;

import nl.hauntedmc.dataregistry.api.player.PlayerIdentity;

import java.util.Objects;

/**
 * Immutable, persistence-free reference to a DataRegistry player.
 *
 * <p>The player id is the canonical identity. UUID and username are optional snapshots intended for
 * display and request handling; they are deliberately not persisted and therefore are absent when a
 * reference is read from a feature database.</p>
 */
public record PlayerReference(Long id, String uuid, String username) {

    public PlayerReference {
        if (id == null || id <= 0L) {
            throw new IllegalArgumentException("id must be positive");
        }
    }

    public static PlayerReference from(PlayerIdentity identity) {
        Objects.requireNonNull(identity, "identity");
        return new PlayerReference(identity.playerId(), identity.uuid().toString(), identity.username());
    }

    public static PlayerReference byId(long playerId) {
        return new PlayerReference(playerId, null, null);
    }

    /**
     * References identify the same player when their canonical DataRegistry ids match. Snapshot fields
     * must not participate: persistence intentionally restores an id-only reference.
     */
    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof PlayerReference reference && id.equals(reference.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    public Long getId() { return id; }
    public String getUuid() { return uuid; }
    public String getUsername() { return username; }
}
