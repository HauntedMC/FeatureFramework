package nl.hauntedmc.featureframework.toolkit.log;

import java.util.Objects;
import java.util.logging.Level;

/** Minimal logging boundary that keeps shared framework code independent of a platform logger. */
public interface FrameworkLogger {

    void info(String message);

    void warn(String message, Throwable failure);

    void error(String message, Throwable failure);

    default void warn(String message) { warn(message, null); }

    default void error(String message) { error(message, null); }

    static FrameworkLogger from(java.util.logging.Logger logger) {
        Objects.requireNonNull(logger, "logger");
        return new FrameworkLogger() {
            @Override public void info(String message) { logger.info(message); }
            @Override public void warn(String message, Throwable failure) { logger.log(Level.WARNING, message, failure); }
            @Override public void error(String message, Throwable failure) { logger.log(Level.SEVERE, message, failure); }
        };
    }

    static FrameworkLogger from(org.slf4j.Logger logger) {
        Objects.requireNonNull(logger, "logger");
        return new FrameworkLogger() {
            @Override public void info(String message) { logger.info(message); }
            @Override public void warn(String message, Throwable failure) { logger.warn(message, failure); }
            @Override public void error(String message, Throwable failure) { logger.error(message, failure); }
        };
    }

    static FrameworkLogger noop() {
        return new FrameworkLogger() {
            @Override public void info(String message) { }
            @Override public void warn(String message, Throwable failure) { }
            @Override public void error(String message, Throwable failure) { }
        };
    }
}
