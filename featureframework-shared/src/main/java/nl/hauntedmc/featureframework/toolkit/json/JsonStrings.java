package nl.hauntedmc.featureframework.toolkit.json;

import java.util.Objects;

/** Small JSON string helper for integrations that intentionally do not need a serializer model. */
public final class JsonStrings {
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private JsonStrings() { }

    public static String escapeJson(String value) {
        Objects.requireNonNull(value, "value");
        StringBuilder escaped = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (character < 0x20) {
                        escaped.append("\\u00")
                                .append(HEX[(character >>> 4) & 0x0f])
                                .append(HEX[character & 0x0f]);
                    } else {
                        escaped.append(character);
                    }
                }
            }
        }
        return escaped.toString();
    }
}
