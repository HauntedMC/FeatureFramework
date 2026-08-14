package nl.hauntedmc.featureframework.toolkit.io.cache;

import nl.hauntedmc.featureframework.toolkit.io.cache.impl.JsonCacheFile;

import java.io.File;
import java.io.IOException;

/** Feature-owned directory under the FeatureFramework cache root. */
public class CacheDirectory {
    private final File directory;

    public CacheDirectory(File baseCacheFolder, String featureName, String cacheId) {
        String safeFeature = sanitizeSegment(featureName, "feature");
        String safeCacheId = sanitizeSegment(cacheId, "cache");
        File candidate = new File(baseCacheFolder, safeFeature + "-" + safeCacheId);
        try {
            File base = baseCacheFolder.getCanonicalFile();
            File normalized = candidate.getCanonicalFile();
            if (!normalized.toPath().startsWith(base.toPath())) {
                throw new IllegalArgumentException("Cache directory escapes base folder");
            }
            directory = normalized;
        } catch (IOException exception) {
            throw new IllegalStateException("Could not resolve cache directory", exception);
        }
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IllegalStateException("Could not create cache directory: " + directory);
        }
    }

    public File getDirectory() { return directory; }

    public CacheStore getStore(String fileName, CacheType type) {
        String safeName = sanitizeSegment(fileName, "store");
        return switch (type) {
            case JSON -> new JsonCacheFile(new File(directory, safeName + ".json"));
        };
    }

    private static String sanitizeSegment(String raw, String def) {
        if (raw == null || raw.isBlank()) return def;
        String normalized = raw.trim().replace('\\', '_').replace('/', '_').replace("..", "_");
        return normalized.isBlank() ? def : normalized;
    }
}
