package nl.hauntedmc.featureframework.toolkit.io.cache;

import java.util.Map;
import java.util.Optional;

/** On-disk cache store with one {@link CacheValue} per key. */
public interface FileCacheStore extends CacheStore {
    void put(String key, CacheValue value);
    CacheValue get(String key);
    default Optional<CacheValue> getOptional(String key) { return Optional.ofNullable(get(key)); }
    default boolean contains(String key) { return get(key) != null; }
    void remove(String key);
    Map<String, CacheValue> listAll();
    Map<String, CacheValue> find(String regex);
}
