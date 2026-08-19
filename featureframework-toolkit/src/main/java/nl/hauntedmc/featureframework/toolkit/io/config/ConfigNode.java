package nl.hauntedmc.featureframework.toolkit.io.config;

import java.util.*;

/** Immutable view over a normalized configuration node. */
public final class ConfigNode {
    private final Object value;
    private final String path;

    private ConfigNode(Object normalizedValue, String path) {
        this.value = normalizedValue;
        this.path = path == null ? "" : path;
    }

    public static ConfigNode ofRaw(Object raw, String path) {
        return new ConfigNode(ConfigTypes.toPlain(raw), path);
    }

    public boolean isNull() { return value == null; }
    public boolean isPresent() { return !isNull(); }
    public boolean isMap() { return value instanceof Map<?, ?>; }
    public boolean isList() { return value instanceof List<?>; }
    public int size() {
        if (value instanceof Map<?, ?> map) return map.size();
        if (value instanceof List<?> list) return list.size();
        return 0;
    }
    public <T> T as(Class<T> type, T defaultValue) { return ConfigTypes.convertOrDefault(value, type, defaultValue); }

    public <T> Optional<T> asOptional(Class<T> type) {
        return Optional.ofNullable(ConfigTypes.convert(value, type));
    }

    public <T> T asRequired(Class<T> type) {
        T converted = ConfigTypes.convert(value, type);
        if (converted == null) throw new IllegalStateException("Required config missing at '" + path + "'");
        return converted;
    }

    public ConfigNode get(String key) {
        if (!(value instanceof Map<?, ?> map)) return new ConfigNode(null, childPath(key));
        return new ConfigNode(ConfigTypes.toPlain(map.get(key)), childPath(key));
    }

    public ConfigNode getAt(String dottedPath) {
        if (dottedPath == null || dottedPath.isBlank()) return this;
        ConfigNode current = this;
        for (String part : dottedPath.split("\\.")) current = current.get(part);
        return current;
    }

    public Set<String> keys() {
        if (!(value instanceof Map<?, ?> map)) return Collections.emptySet();
        LinkedHashSet<String> output = new LinkedHashSet<>();
        for (Object key : map.keySet()) output.add(String.valueOf(key));
        return output;
    }

    public Map<String, ConfigNode> children() {
        if (!(value instanceof Map<?, ?> map)) return Map.of();
        LinkedHashMap<String, ConfigNode> output = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String key = String.valueOf(entry.getKey());
            output.put(key, new ConfigNode(ConfigTypes.toPlain(entry.getValue()), childPath(key)));
        }
        return output;
    }

    public <T> List<T> listOf(Class<T> elementType) { return ConfigTypes.convertList(value, elementType); }
    public <V> Map<String, V> mapValues(Class<V> valueType) { return ConfigTypes.convertMapValues(value, valueType); }

    /** Combines scalar-or-list values stored under the requested child keys. */
    public List<String> mergedStringList(String... keys) {
        if (!(value instanceof Map<?, ?> map) || keys == null) return List.of();
        ArrayList<String> output = new ArrayList<>();
        for (String key : keys) {
            Object raw = map.get(key);
            if (raw != null) output.addAll(ConfigTypes.convertList(raw, String.class));
        }
        return List.copyOf(output);
    }
    public Object raw() { return value; }
    public String path() { return path; }
    private String childPath(String key) { return path.isEmpty() ? key : path + "." + key; }

    @Override
    public String toString() { return "ConfigNode(" + path + ")"; }
}
