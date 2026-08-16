package nl.hauntedmc.featureframework.theme;

import java.util.Objects;

/** One named colour operation in a theme. */
public record ThemeItem(ThemeItemId id, ThemeColor color) {
    public ThemeItem {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(color, "color");
    }
}
