package nl.hauntedmc.featureframework.toolkit.text.placeholder;

import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MessagePlaceholdersTest {

    @Test
    void builderSupportsTypedValuesAndLongestKeysAreAppliedFirst() {
        MessagePlaceholders placeholders = MessagePlaceholders.builder()
                .addString("player", "Alice")
                .addNumber("count", 3)
                .addComponent("component", Component.text("Hello"))
                .add("nullable", null)
                .addAll(MessagePlaceholders.of("player_name", "AliceTheGreat"))
                .build();

        assertEquals("Alice", placeholders.get("player"));
        assertTrue(placeholders.contains("count"));
        assertEquals(5, placeholders.size());
        assertFalse(placeholders.isEmpty());
        assertEquals(placeholders.get("player"), placeholders.asMap().get("player"));
        assertEquals("AliceTheGreat/Alice/3", placeholders.apply("{player_name}/{player}/{count}"));
        assertTrue(placeholders.get("component").contains("Hello"));
        assertEquals("", placeholders.get("nullable"));
        assertTrue(placeholders.toString().contains("player"));
        assertThrows(UnsupportedOperationException.class, () -> placeholders.asMap().put("x", "y"));
    }

    @Test
    void emptyAndMapFactoriesAreSafe() {
        assertSame(MessagePlaceholders.empty(), MessagePlaceholders.empty());
        assertTrue(MessagePlaceholders.empty().isEmpty());
        assertEquals("unchanged", MessagePlaceholders.applyPlaceholders("unchanged", MessagePlaceholders.empty()));
        assertNull(MessagePlaceholders.applyPlaceholders(null, MessagePlaceholders.empty()));
        assertEquals("x", MessagePlaceholders.of(Map.of("k", "x")).get("k"));
    }
}
