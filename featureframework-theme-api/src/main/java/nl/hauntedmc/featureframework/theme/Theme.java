package nl.hauntedmc.featureframework.theme;

import net.kyori.adventure.text.format.TextColor;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Immutable collection of named colour operations. */
public final class Theme {
    private final ThemeId id;
    private final Map<String, ThemeItem> itemsByKey;
    private final List<ThemeItem> items;

    private Theme(Builder builder) {
        id = builder.id;
        if (builder.itemsByKey.isEmpty()) {
            throw new IllegalArgumentException("theme must contain at least one item");
        }
        itemsByKey = Collections.unmodifiableMap(new LinkedHashMap<>(builder.itemsByKey));
        items = List.copyOf(itemsByKey.values());
    }

    public static Builder builder(String id) {
        return builder(ThemeId.of(id));
    }

    public static Builder builder(ThemeId id) {
        return new Builder(id);
    }

    public ThemeId id() {
        return id;
    }

    public List<ThemeItem> items() {
        return items;
    }

    public int size() {
        return items.size();
    }

    public Optional<ThemeItem> item(String itemId) {
        return item(ThemeItemId.of(itemId));
    }

    public Optional<ThemeItem> item(ThemeItemId itemId) {
        Objects.requireNonNull(itemId, "itemId");
        return Optional.ofNullable(itemsByKey.get(itemId.lookupKey()));
    }

    /** Fluent builder that rejects case-insensitive duplicate item identifiers. */
    public static final class Builder {
        private final ThemeId id;
        private final Map<String, ThemeItem> itemsByKey = new LinkedHashMap<>();

        private Builder(ThemeId id) {
            this.id = Objects.requireNonNull(id, "id");
        }

        public Builder item(String itemId, ThemeColor color) {
            return item(ThemeItemId.of(itemId), color);
        }

        public Builder item(ThemeItemId itemId, ThemeColor color) {
            return item(new ThemeItem(itemId, color));
        }

        public Builder item(ThemeItem item) {
            ThemeItem value = Objects.requireNonNull(item, "item");
            ThemeItem previous = itemsByKey.putIfAbsent(value.id().lookupKey(), value);
            if (previous != null) {
                throw new IllegalArgumentException("duplicate theme item identifier: " + value.id().value());
            }
            return this;
        }

        public Builder items(Iterable<? extends ThemeItem> values) {
            Objects.requireNonNull(values, "items").forEach(this::item);
            return this;
        }

        public Builder solid(String itemId, TextColor color) {
            return item(itemId, ThemeColor.solid(color));
        }

        public Builder solid(String itemId, int rgb) {
            return item(itemId, ThemeColor.solid(rgb));
        }

        public Builder solid(String itemId, String hex) {
            return item(itemId, ThemeColor.solid(hex));
        }

        public Builder gradient(String itemId, List<? extends TextColor> colors) {
            return item(itemId, ThemeColor.gradient(colors));
        }

        public Builder gradient(String itemId, List<? extends TextColor> colors, double phase) {
            return item(itemId, ThemeColor.gradient(colors, phase));
        }

        public Builder transition(String itemId, List<? extends TextColor> colors, double phase) {
            return item(itemId, ThemeColor.transition(colors, phase));
        }

        public Builder transition(String itemId, List<? extends TextColor> colors) {
            return item(itemId, ThemeColor.transition(colors));
        }

        public Builder rainbow(String itemId) {
            return item(itemId, ThemeColor.rainbow());
        }

        public Builder rainbow(String itemId, int phase, boolean reversed) {
            return item(itemId, ThemeColor.rainbow(phase, reversed));
        }

        public Theme build() {
            return new Theme(this);
        }
    }
}
