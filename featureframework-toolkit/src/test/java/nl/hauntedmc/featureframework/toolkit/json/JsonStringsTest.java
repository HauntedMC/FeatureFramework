package nl.hauntedmc.featureframework.toolkit.json;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JsonStringsTest {
    @Test
    void escapeJsonEscapesQuotesBackslashesAndControlCharacters() {
        assertEquals("a\\\\b\\\"c\\b\\f\\n\\r\\t\\u0001d",
                JsonStrings.escapeJson("a\\b\"c\b\f\n\r\t\u0001d"));
    }

    @Test
    void escapeJsonRejectsNull() {
        assertThrows(NullPointerException.class, () -> JsonStrings.escapeJson(null));
    }
}
