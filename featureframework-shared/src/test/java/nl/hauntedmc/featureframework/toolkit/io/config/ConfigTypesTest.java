package nl.hauntedmc.featureframework.toolkit.io.config;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ConfigTypesTest {
    private enum Mode { ALPHA, BETA }

    @Test
    void deepNormalizationAndScalarConversionsWork() {
        Object normalized = ConfigTypes.toPlain(Map.of("k", List.of(Map.of("x", 1))));
        assertInstanceOf(Map.class, normalized);
        assertInstanceOf(List.class, ((Map<?, ?>) normalized).get("k"));
        assertEquals("1", ConfigTypes.convert(1, String.class));
        assertEquals(true, ConfigTypes.convert("true", Boolean.class));
        assertEquals(true, ConfigTypes.convert(1, Boolean.class));
        assertEquals(5, ConfigTypes.convert("5", Integer.class));
        assertEquals(6L, ConfigTypes.convert("6", Long.class));
        assertEquals(2.5d, ConfigTypes.convert("2.5", Double.class));
        assertEquals(2.5f, ConfigTypes.convert("2.5", Float.class));
        assertEquals(3.25f, ConfigTypes.convert(3.25d, Float.class));
        assertEquals(Mode.ALPHA, ConfigTypes.convert("alpha", Mode.class));
        assertEquals(List.of(1, 2), ConfigTypes.convertList(List.of("1", 2), Integer.class));
        assertEquals(List.of(7), ConfigTypes.convertList("7", Integer.class));
        assertEquals(Map.of("a", 1), ConfigTypes.convertMapValues(Map.of("a", "1"), Integer.class));
    }

    @Test
    void invalidConversionsFailAndDefaultsRemainSafe() {
        assertThrows(IllegalArgumentException.class, () -> ConfigTypes.convert("abc", Integer.class));
        assertThrows(IllegalArgumentException.class, () -> ConfigTypes.convert("abc", Float.class));
        assertThrows(IllegalArgumentException.class, () -> ConfigTypes.convert("yes", Boolean.class));
        assertThrows(IllegalArgumentException.class, () -> ConfigTypes.convert("missing", Mode.class));
        assertThrows(IllegalArgumentException.class, () -> ConfigTypes.convert("x", Map.class));
        IllegalArgumentException element = assertThrows(IllegalArgumentException.class,
                () -> ConfigTypes.convertList(List.of("x"), Integer.class));
        assertTrue(element.getMessage().contains("index 0"));
        assertEquals(9, ConfigTypes.convertOrDefault("x", Integer.class, 9));
        assertEquals(true, ConfigTypes.convertOrDefault("yes", Boolean.class, true));
        assertNull(ConfigTypes.convertList(null, Integer.class));
        assertNull(ConfigTypes.convertMapValues(null, Integer.class));
    }
}
