package nl.hauntedmc.featureframework.toolkit.io.config;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ConfigMapTest {
    @Test
    void putGetTypedGetAndCollectionViewsWork() {
        ConfigMap map = new ConfigMap()
                .put("name", "server")
                .putAll(Map.of("enabled", true, "count", 3));
        assertEquals("server", map.get("name"));
        assertEquals("server", map.get("name", String.class));
        assertNull(map.get("missing", String.class));
        assertTrue(map.contains("enabled"));
        assertFalse(map.isEmpty());
        assertEquals(3, map.size());
        assertTrue(map.keySet().contains("count"));
        assertTrue(map.entrySet().stream().anyMatch(entry -> entry.getKey().equals("name")));
        Map<String, Object> copy = map.toMap();
        copy.put("other", 1);
        assertFalse(map.contains("other"));
        AtomicInteger seen = new AtomicInteger();
        map.forEach((key, value) -> seen.incrementAndGet());
        assertEquals(3, seen.get());
        assertTrue(map.toString().contains("server"));
        assertTrue(new ConfigMap().isEmpty());
    }

    @Test
    void typedGetRejectsTypeMismatch() {
        ConfigMap map = new ConfigMap().put("count", 3);
        assertThrows(ClassCastException.class, () -> map.get("count", String.class));
    }
}
