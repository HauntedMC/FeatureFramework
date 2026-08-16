package nl.hauntedmc.featureframework.theme;

import java.util.Locale;

/** Validated identifier for one colour item inside a theme. */
public record ThemeItemId(String value) {
    public ThemeItemId {
        value = ThemeId.validate(value, "theme item identifier");
    }

    public static ThemeItemId of(String value) {
        return new ThemeItemId(value);
    }

    String lookupKey() {
        return value.toLowerCase(Locale.ROOT);
    }
}
