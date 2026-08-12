package nl.hauntedmc.featureframework.toolkit.io.cache;

import java.io.File;

/** Common lifecycle operations for an on-disk cache store. */
public interface CacheStore {
    File getUnderlyingFile();
    void cleanupExpired();
    void delete();
    boolean isEmpty();
}
