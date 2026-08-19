package nl.hauntedmc.featureframework.theme;

import java.util.Locale;
import java.util.Optional;

/** Validated identifier for one colour item inside a theme. */
public record ThemeItemId(String value) {
    public ThemeItemId {
        value = ThemeId.validate(value, "theme item identifier");
    }

    public static ThemeItemId of(String value) {
        return new ThemeItemId(value);
    }

    /** Returns whether a raw value can be normalized into a valid theme item identifier. */
    public static boolean isValid(String value) {
        return value != null && ThemeId.isNormalizedValueValid(value.trim());
    }

    /** Parses a theme item identifier without throwing for null or malformed external input. */
    public static Optional<ThemeItemId> tryParse(String value) {
        return isValid(value) ? Optional.of(new ThemeItemId(value)) : Optional.empty();
    }

    String lookupKey() {
        return value.toLowerCase(Locale.ROOT);
    }
}
