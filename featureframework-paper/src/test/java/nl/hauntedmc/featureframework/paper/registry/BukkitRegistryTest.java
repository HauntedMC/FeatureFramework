package nl.hauntedmc.featureframework.paper.registry;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class BukkitRegistryTest {

    @Test
    void namespacedKeyParsingAcceptsOnlyCanonicalIdentifiers() {
        assertEquals("minecraft:entity.player.levelup",
                BukkitRegistry.parseNamespacedKey("minecraft:entity.player.levelup").asString());
        assertEquals("custom:sound", BukkitRegistry.parseNamespacedKey("custom:sound").asString());

        assertNull(BukkitRegistry.parseNamespacedKey(null));
        assertNull(BukkitRegistry.parseNamespacedKey(""));
        assertNull(BukkitRegistry.parseNamespacedKey(" entity.player.levelup "));
        assertNull(BukkitRegistry.parseNamespacedKey("entity.player.levelup"));
        assertNull(BukkitRegistry.parseNamespacedKey("ENTITY_PLAYER_LEVELUP"));
        assertNull(BukkitRegistry.parseNamespacedKey("Minecraft:entity.player.levelup"));
    }
}
