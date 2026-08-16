package nl.hauntedmc.featureframework.theme;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/** Validated identifier for a registered theme. */
public record ThemeId(String value) {
    private static final Pattern VALID_IDENTIFIER = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_-]*");

    public ThemeId {
        value = validate(value, "theme identifier");
    }

    public static ThemeId of(String value) {
        return new ThemeId(value);
    }

    String lookupKey() {
        return value.toLowerCase(Locale.ROOT);
    }

    static String validate(String value, String description) {
        String candidate = Objects.requireNonNull(value, description).trim();
        if (!VALID_IDENTIFIER.matcher(candidate).matches()) {
            throw new IllegalArgumentException(description
                    + " must start with an alphanumeric character and contain only alphanumerics, '_' or '-'");
        }
        return candidate;
    }
}
