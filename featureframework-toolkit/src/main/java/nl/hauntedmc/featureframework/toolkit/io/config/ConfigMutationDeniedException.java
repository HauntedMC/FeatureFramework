package nl.hauntedmc.featureframework.toolkit.io.config;

import java.nio.file.Path;
import java.util.Objects;

/** Structured denial raised when the active host policy forbids a configuration mutation. */
public class ConfigMutationDeniedException extends RuntimeException {
    private final Path relativePath;
    private final String operation;

    public ConfigMutationDeniedException(Path relativePath, String operation, String message) {
        super(message);
        this.relativePath = Objects.requireNonNull(relativePath, "relativePath");
        this.operation = Objects.requireNonNull(operation, "operation");
    }

    public Path relativePath() { return relativePath; }
    public String operation() { return operation; }
}
