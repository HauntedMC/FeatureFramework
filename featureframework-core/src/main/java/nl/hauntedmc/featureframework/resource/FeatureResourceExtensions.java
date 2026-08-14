package nl.hauntedmc.featureframework.resource;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Typed extensions attached to one feature generation by host contributors. */
public final class FeatureResourceExtensions {
    private final Map<ResourceKey<?>, Object> values = new LinkedHashMap<>();

    public synchronized <T> void register(ResourceKey<T> key, T value) {
        ResourceKey<T> requiredKey = Objects.requireNonNull(key, "key");
        T requiredValue = requiredKey.type().cast(Objects.requireNonNull(value, "value"));
        if (values.putIfAbsent(requiredKey, requiredValue) != null) {
            throw new IllegalStateException("Resource extension is already registered: " + requiredKey.id());
        }
    }

    public synchronized <T> Optional<T> find(ResourceKey<T> key) {
        ResourceKey<T> requiredKey = Objects.requireNonNull(key, "key");
        return Optional.ofNullable(values.get(requiredKey)).map(requiredKey.type()::cast);
    }

    public <T> T require(ResourceKey<T> key) {
        return find(key).orElseThrow(() -> new IllegalStateException(
                "Required resource extension is unavailable: " + key.id()));
    }

    public synchronized boolean contains(Class<?> type) {
        return values.keySet().stream().anyMatch(key -> key.type().equals(type));
    }
}
