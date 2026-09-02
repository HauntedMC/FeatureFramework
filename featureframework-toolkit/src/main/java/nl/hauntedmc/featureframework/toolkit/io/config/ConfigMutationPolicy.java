package nl.hauntedmc.featureframework.toolkit.io.config;

import java.nio.file.Path;
import java.util.Objects;

/** Host policy invoked immediately before FeatureFramework mutates a configuration file. */
@FunctionalInterface
public interface ConfigMutationPolicy {
    void checkMutation(Path relativePath, String operation);

    static ConfigMutationPolicy allowAll() {
        return (path, operation) -> {
            Objects.requireNonNull(path, "path");
            Objects.requireNonNull(operation, "operation");
        };
    }
}
