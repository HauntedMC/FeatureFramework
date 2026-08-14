package nl.hauntedmc.featureframework.toolkit.io.config;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ConfigNodeTest {
    @Test
    void traversalAndTypedChildViewsWork() {
        ConfigNode root = ConfigNode.ofRaw(Map.of(
                "global", Map.of("name", "server"),
                "list", List.of("1", "2"),
                "weights", Map.of("a", "1")
        ), "root");
        assertFalse(root.isNull());
        assertEquals("server", root.getAt("global.name").asRequired(String.class));
        assertEquals(List.of("1", "2"), root.get("list").listOf(String.class));
        assertEquals(Map.of("a", 1), root.get("weights").mapValues(Integer.class));
        assertTrue(root.keys().contains("global"));
        assertTrue(root.children().containsKey("global"));
        assertEquals("root.global", root.get("global").path());
    }

    @Test
    void missingAndNonMapNodesAreSafe() {
        ConfigNode missing = ConfigNode.ofRaw(null, "x");
        assertTrue(missing.isNull());
        assertNull(missing.raw());
        assertEquals("fallback", missing.as(String.class, "fallback"));
        assertThrows(IllegalStateException.class, () -> missing.asRequired(String.class));
        ConfigNode root = ConfigNode.ofRaw(Map.of("a", 1), null);
        assertSame(root, root.getAt(" "));
        assertEquals("a", root.get("a").path());
        assertTrue(ConfigNode.ofRaw(1, "n").get("child").isNull());
    }
}
