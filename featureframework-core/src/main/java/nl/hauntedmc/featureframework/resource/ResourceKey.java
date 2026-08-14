package nl.hauntedmc.featureframework.resource;

import java.util.Objects;

/** Stable typed identity for an optional feature resource extension. */
public record ResourceKey<T>(String id, Class<T> type) {
    public ResourceKey {
        id = requireText(id);
        type = Objects.requireNonNull(type, "type");
    }

    public static <T> ResourceKey<T> of(Class<T> type) {
        Class<T> required = Objects.requireNonNull(type, "type");
        return new ResourceKey<>(required.getName(), required);
    }

    private static String requireText(String value) {
        String normalized = Objects.requireNonNull(value, "id").trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException("id must not be blank");
        return normalized;
    }
}
