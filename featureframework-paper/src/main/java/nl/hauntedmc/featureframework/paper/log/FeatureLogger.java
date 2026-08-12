package nl.hauntedmc.featureframework.paper.log;

import nl.hauntedmc.featureframework.toolkit.log.FrameworkLogger;

import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Java Util Logging adapter that prefixes every message with its owning feature name. */
public class FeatureLogger implements FrameworkLogger {
    private final Logger delegate;
    private final String prefix;

    public FeatureLogger(Logger delegate, String featureName) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.prefix = "[" + Objects.requireNonNull(featureName, "featureName") + "] ";
    }

    @Override public void info(String message) { delegate.info(prefix + message); }
    public void warning(String message) { delegate.warning(prefix + message); }
    public void severe(String message) { delegate.severe(prefix + message); }
    public void fine(String message) { delegate.fine(prefix + message); }
    public void log(Level level, String message) { delegate.log(level, prefix + message); }
    public void log(Level level, String message, Throwable failure) { delegate.log(level, prefix + message, failure); }
    @Override public void warn(String message, Throwable failure) {
        delegate.log(Level.WARNING, prefix + message, failure);
    }
    @Override public void error(String message, Throwable failure) {
        delegate.log(Level.SEVERE, prefix + message, failure);
    }
}
