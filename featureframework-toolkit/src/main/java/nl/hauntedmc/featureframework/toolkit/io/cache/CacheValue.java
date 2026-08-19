package nl.hauntedmc.featureframework.toolkit.io.cache;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Immutable cached value and its expiry timestamp. */
public final class CacheValue {
    private final Map<String, Object> data;
    private final long expirationTimestamp;

    private CacheValue(Map<String, Object> data, long expirationTimestamp) {
        this.data = data;
        this.expirationTimestamp = expirationTimestamp;
    }

    public static CacheValue of(Map<String, Object> data, long expirationTimestamp) {
        return new CacheValue(Map.copyOf(Objects.requireNonNull(data, "data")), expirationTimestamp);
    }

    public Map<String, Object> getData() { return data; }
    public Object get(String key) { return data.get(key); }
    public <T> T get(String key, Class<T> type) {
        Object value = data.get(key);
        return value == null ? null : Objects.requireNonNull(type, "type").cast(value);
    }
    public boolean contains(String key) { return data.containsKey(key); }
    public int size() { return data.size(); }
    public boolean isEmpty() { return data.isEmpty(); }
    public long getExpirationTimestamp() { return expirationTimestamp; }
    public boolean isExpired() { return isExpired(System.currentTimeMillis()); }
    public boolean isExpired(long nowMillis) {
        return expirationTimestamp > 0 && nowMillis > expirationTimestamp;
    }
    public static Builder builder(long ttlMillis) { return new Builder(ttlMillis); }

    public static final class Builder {
        private final Map<String, Object> data = new LinkedHashMap<>();
        private final long ttlMillis;

        private Builder(long ttlMillis) {
            if (ttlMillis < 0) throw new IllegalArgumentException("ttlMillis >= 0");
            this.ttlMillis = ttlMillis;
        }

        public Builder with(String key, Object value) {
            data.put(Objects.requireNonNull(key, "key"), value);
            return this;
        }

        public Builder withAll(Map<String, ?> values) {
            Objects.requireNonNull(values, "values").forEach(this::with);
            return this;
        }

        public CacheValue build() {
            return new CacheValue(Map.copyOf(data), System.currentTimeMillis() + ttlMillis);
        }
    }
}
