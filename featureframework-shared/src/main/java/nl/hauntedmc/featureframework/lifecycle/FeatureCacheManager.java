package nl.hauntedmc.featureframework.lifecycle;

import nl.hauntedmc.featureframework.toolkit.io.cache.CacheDirectory;
import nl.hauntedmc.featureframework.toolkit.ToolkitContext;
import nl.hauntedmc.featureframework.toolkit.log.FrameworkLogger;

import java.io.File;
import java.nio.file.Path;
import java.util.Objects;

/** Manages a runtime's top-level cache folder and feature-scoped cache directories. */
public class FeatureCacheManager {
    private final File baseFolder;
    private FeatureResourceState state = FeatureResourceState.OPEN;

    public FeatureCacheManager(ToolkitContext context) {
        this(
                Objects.requireNonNull(context, "context").getDataDirectory(),
                Objects.requireNonNullElse(context.getToolkitLogger(), FrameworkLogger.noop())
        );
    }

    public FeatureCacheManager(Path dataDirectory, FrameworkLogger logger) {
        Objects.requireNonNull(dataDirectory, "dataDirectory");
        Objects.requireNonNull(logger, "logger");
        this.baseFolder = dataDirectory.resolve("cache").toFile();
        if (baseFolder.exists() && !baseFolder.isDirectory()) {
            throw new IllegalStateException("Cache path is not a directory: " + baseFolder);
        }
        if (!baseFolder.exists()) {
            if (!baseFolder.mkdirs()) {
                throw new IllegalStateException("Could not create cache folder: " + baseFolder);
            }
            logger.info("Created cache folder at " + baseFolder);
        }
    }

    public synchronized CacheDirectory getCacheDirectory(String featureName, String cacheId) {
        requireOpen();
        return new CacheDirectory(baseFolder, featureName, cacheId);
    }

    public synchronized void quiesce() {
        if (state == FeatureResourceState.OPEN) {
            state = FeatureResourceState.QUIESCING;
        }
    }

    public synchronized void cleanupAll() {
        quiesce();
        state = FeatureResourceState.CLOSED;
    }

    public synchronized FeatureResourceState state() {
        return state;
    }

    private void requireOpen() {
        if (state != FeatureResourceState.OPEN) {
            throw new IllegalStateException("Cache manager is " + state);
        }
    }
}
