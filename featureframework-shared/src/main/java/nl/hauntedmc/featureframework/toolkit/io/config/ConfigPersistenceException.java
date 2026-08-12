package nl.hauntedmc.featureframework.toolkit.io.config;

import java.nio.file.Path;
import java.util.Objects;

/** Raised when a configuration mutation cannot be durably persisted. */
public final class ConfigPersistenceException extends IllegalStateException {
    private static final long serialVersionUID = 1L;
    private final String path;

    public ConfigPersistenceException(Path path, String operation, Throwable cause) {
        super("Unable to " + Objects.requireNonNull(operation, "operation")
                + " configuration file: " + Objects.requireNonNull(path, "path"), cause);
        this.path = path.toString();
    }

    public Path path() {
        return Path.of(path);
    }
}
