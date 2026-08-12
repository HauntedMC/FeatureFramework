package nl.hauntedmc.featureframework.toolkit.io.config;

import java.nio.file.Path;
import java.util.Objects;

/** Raised when a YAML file cannot be parsed without replacing its last-known-good state. */
public final class ConfigLoadException extends IllegalStateException {
    private static final long serialVersionUID = 1L;
    private final String path;

    public ConfigLoadException(Path path, Throwable cause) {
        super("Unable to load configuration file: " + Objects.requireNonNull(path, "path"), cause);
        this.path = path.toString();
    }

    public Path path() {
        return Path.of(path);
    }
}
