package nl.hauntedmc.featureframework.theme;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
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

    /** Returns whether a raw value can be normalized into a valid theme identifier. */
    public static boolean isValid(String value) {
        return value != null && isNormalizedValueValid(value.trim());
    }

    /** Parses a theme identifier without throwing for null or malformed external input. */
    public static Optional<ThemeId> tryParse(String value) {
        return isValid(value) ? Optional.of(new ThemeId(value)) : Optional.empty();
    }

    String lookupKey() {
        return value.toLowerCase(Locale.ROOT);
    }

    static String validate(String value, String description) {
        String candidate = Objects.requireNonNull(value, description).trim();
        if (!isNormalizedValueValid(candidate)) {
            throw new IllegalArgumentException(description
                    + " must start with an alphanumeric character and contain only alphanumerics, '_' or '-'");
        }
        return candidate;
    }

    static boolean isNormalizedValueValid(String value) {
        return VALID_IDENTIFIER.matcher(value).matches();
    }
}
