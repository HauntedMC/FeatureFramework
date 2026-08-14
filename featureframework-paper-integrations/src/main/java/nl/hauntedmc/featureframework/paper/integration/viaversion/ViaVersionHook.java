package nl.hauntedmc.featureframework.paper.integration.viaversion;

import com.viaversion.viaversion.api.Via;
import com.viaversion.viaversion.api.ViaAPI;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import org.bukkit.entity.Player;

import java.util.Objects;
import java.util.UUID;

/**
 * Thin wrapper around ViaVersion to keep external API usage contained.
 */
public final class ViaVersionHook {

    private final ProtocolLookup lookup;

    public ViaVersionHook() {
        this(new ViaProtocolLookup(Via.getAPI()));
    }

    ViaVersionHook(ProtocolLookup lookup) {
        this.lookup = Objects.requireNonNull(lookup, "lookup");
    }

    public boolean isAvailable() {
        return lookup.isAvailable();
    }

    public int getServerNativeProtocolId() {
        ensureAvailable();
        return lookup.serverProtocolId();
    }

    public String getServerNativeProtocolName() {
        ensureAvailable();
        return safeNameForId(getServerNativeProtocolId());
    }

    public int getClientProtocolId(Player player) {
        Objects.requireNonNull(player, "player");
        ensureAvailable();
        return lookup.clientProtocolId(player.getUniqueId());
    }

    public String getClientProtocolName(Player player) {
        return safeNameForId(getClientProtocolId(player));
    }

    private String safeNameForId(int id) {
        ProtocolVersion pv = ProtocolVersion.getProtocol(id);
        return pv.getName();
    }

    private void ensureAvailable() {
        if (!isAvailable()) throw new IllegalStateException("ViaVersion is not available");
    }

    interface ProtocolLookup {
        boolean isAvailable();

        int serverProtocolId();

        int clientProtocolId(UUID playerId);
    }

    private static final class ViaProtocolLookup implements ProtocolLookup {
        private final ViaAPI<?> api;

        private ViaProtocolLookup(ViaAPI<?> api) {
            this.api = api;
        }

        @Override
        public boolean isAvailable() {
            return api != null;
        }

        @Override
        public int serverProtocolId() {
            return api.getServerVersion().highestSupportedProtocolVersion().getVersion();
        }

        @Override
        public int clientProtocolId(UUID playerId) {
            return api.getPlayerVersion(playerId);
        }
    }
}
