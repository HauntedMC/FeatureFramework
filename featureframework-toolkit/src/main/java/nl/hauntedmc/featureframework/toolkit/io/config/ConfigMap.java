package nl.hauntedmc.featureframework.toolkit.io.config;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

/** Type-safe wrapper around a configurable key-value map. */
public class ConfigMap {
    private final Map<String, Object> values = new HashMap<>();

    public ConfigMap put(String key, Object value) {
        values.put(key, value);
        return this;
    }

    /** Adds all supplied values and returns this map for fluent default construction. */
    public ConfigMap putAll(Map<String, ?> values) {
        this.values.putAll(values);
        return this;
    }

    public Object get(String key) {
        return values.get(key);
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String key, Class<T> type) {
        Object value = values.get(key);
        if (value == null) return null;
        if (!type.isInstance(value)) {
            throw new ClassCastException("Config key '" + key + "' is not of type " + type.getSimpleName());
        }
        return (T) value;
    }

    public boolean contains(String key) { return values.containsKey(key); }
    public boolean isEmpty() { return values.isEmpty(); }
    public int size() { return values.size(); }
    public Set<String> keySet() { return values.keySet(); }
    public Set<Map.Entry<String, Object>> entrySet() { return values.entrySet(); }
    public Map<String, Object> toMap() { return new HashMap<>(values); }
    public void forEach(BiConsumer<String, Object> action) { values.forEach(action); }

    @Override
    public String toString() { return values.toString(); }
}
