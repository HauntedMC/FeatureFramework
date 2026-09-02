package nl.hauntedmc.featureframework.toolkit.io.config;

import java.nio.file.Path;
import java.util.Objects;

/** Structured denial raised when the active host policy forbids a configuration mutation. */
public class ConfigMutationDeniedException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final String relativePath;
    private final String operation;

    public ConfigMutationDeniedException(Path relativePath, String operation, String message) {
        super(message);
        this.relativePath = Objects.requireNonNull(relativePath, "relativePath").toString();
        this.operation = Objects.requireNonNull(operation, "operation");
    }

    public Path relativePath() { return Path.of(relativePath); }
    public String operation() { return operation; }
}