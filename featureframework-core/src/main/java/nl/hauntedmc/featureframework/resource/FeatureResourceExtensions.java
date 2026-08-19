package nl.hauntedmc.featureframework.resource;

import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

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

    /** Returns whether the exact typed resource key is registered. */
    public synchronized boolean containsKey(ResourceKey<?> key) {
        return values.containsKey(Objects.requireNonNull(key, "key"));
    }

    /** Returns an immutable snapshot of the currently registered resource keys. */
    public synchronized Set<ResourceKey<?>> keys() {
        return java.util.Collections.unmodifiableSet(new LinkedHashSet<>(values.keySet()));
    }

    public synchronized int size() {
        return values.size();
    }

    public synchronized boolean isEmpty() {
        return values.isEmpty();
    }
}
