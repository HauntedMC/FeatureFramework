package nl.hauntedmc.featureframework.theme;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Immutable registry of themes loaded into one FeatureFramework host. */
public final class ThemeRegistry {
    private static final ThemeRegistry EMPTY = new ThemeRegistry(Map.of());

    private final Map<String, Theme> themesByKey;
    private final List<Theme> themes;

    private ThemeRegistry(Map<String, Theme> themesByKey) {
        this.themesByKey = Collections.unmodifiableMap(new LinkedHashMap<>(themesByKey));
        themes = List.copyOf(this.themesByKey.values());
    }

    public static ThemeRegistry empty() {
        return EMPTY;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static ThemeRegistry of(Iterable<? extends Theme> themes) {
        Objects.requireNonNull(themes, "themes");
        Map<String, Theme> indexed = new LinkedHashMap<>();
        for (Theme theme : themes) {
            Theme value = Objects.requireNonNull(theme, "theme");
            String key = value.id().lookupKey();
            Theme previous = indexed.putIfAbsent(key, value);
            if (previous != null) {
                throw new IllegalArgumentException("duplicate theme identifier: " + value.id().value());
            }
        }
        return indexed.isEmpty() ? EMPTY : new ThemeRegistry(indexed);
    }

    public boolean isEmpty() {
        return themesByKey.isEmpty();
    }

    public List<Theme> themes() {
        return themes;
    }

    public Optional<Theme> theme(String id) {
        return theme(ThemeId.of(id));
    }

    public Optional<Theme> theme(ThemeId id) {
        Objects.requireNonNull(id, "id");
        return Optional.ofNullable(themesByKey.get(id.lookupKey()));
    }

    public Optional<ThemeItem> item(String themeId, String itemId) {
        return theme(themeId).flatMap(theme -> theme.item(itemId));
    }

    public static final class Builder {
        private final List<Theme> themes = new ArrayList<>();

        public Builder theme(Theme theme) {
            themes.add(Objects.requireNonNull(theme, "theme"));
            return this;
        }

        public Builder themes(Iterable<? extends Theme> values) {
            Objects.requireNonNull(values, "themes").forEach(this::theme);
            return this;
        }

        public Builder include(ThemeRegistry registry) {
            themes(Objects.requireNonNull(registry, "registry").themes());
            return this;
        }

        public ThemeRegistry build() {
            return ThemeRegistry.of(themes);
        }
    }
}
