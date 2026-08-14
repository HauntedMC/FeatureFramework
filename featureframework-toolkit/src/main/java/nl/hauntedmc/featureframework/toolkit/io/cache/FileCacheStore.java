package nl.hauntedmc.featureframework.toolkit.io.cache;

import java.util.Map;

/** On-disk cache store with one {@link CacheValue} per key. */
public interface FileCacheStore extends CacheStore {
    void put(String key, CacheValue value);
    CacheValue get(String key);
    void remove(String key);
    Map<String, CacheValue> listAll();
    Map<String, CacheValue> find(String regex);
}
