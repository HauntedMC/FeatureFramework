package nl.hauntedmc.featureframework.toolkit;

import nl.hauntedmc.featureframework.toolkit.log.FrameworkLogger;

import java.nio.file.Path;

/** Minimal host contract required by toolkit configuration and resource services. */
public interface ToolkitContext {
    Path getDataDirectory();
    default FrameworkLogger getToolkitLogger() {
        return FrameworkLogger.noop();
    }

    default ClassLoader getResourceClassLoader() {
        return getClass().getClassLoader();
    }
}
