package nl.hauntedmc.featureframework.toolkit.io.cache;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CacheValueTest {
    @Test
    void builderCreatesImmutableValuesAndExpirationIsObserved() {
        assertThrows(IllegalArgumentException.class, () -> CacheValue.builder(-1));
        CacheValue built = CacheValue.builder(50).with("name", "server").with("score", 7).build();
        assertEquals("server", built.getData().get("name"));
        assertThrows(UnsupportedOperationException.class, () -> built.getData().put("x", "y"));
        long now = System.currentTimeMillis();
        assertTrue(CacheValue.of(Map.of("a", 1), now - 1).isExpired());
        assertFalse(CacheValue.of(Map.of("a", 1), now + 60_000).isExpired());
        assertThrows(NullPointerException.class, () -> CacheValue.of(null, 0));
        assertThrows(NullPointerException.class, () -> CacheValue.builder(1).with(null, "x"));
    }
}
