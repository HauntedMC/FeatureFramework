package nl.hauntedmc.featureframework.toolkit.io.localization;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageMapTest {
    @Test
    void supportsFluentAndBulkDefaultConstruction() {
        Map<String, String> additional = new LinkedHashMap<>();
        additional.put("second", "Two");
        additional.put("third", "Three");

        MessageMap messages = new MessageMap()
                .put("first", "One")
                .putAll(additional);

        assertEquals(3, messages.size());
        assertFalse(messages.isEmpty());
        assertTrue(messages.contains("second"));
        assertEquals("One", messages.getMessages().get("first"));
        assertTrue(new MessageMap().isEmpty());
    }
}
