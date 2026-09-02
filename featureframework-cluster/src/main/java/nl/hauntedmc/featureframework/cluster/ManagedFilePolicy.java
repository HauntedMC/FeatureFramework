package nl.hauntedmc.featureframework.cluster;

import java.nio.file.Path;

/** Decides whether one path belongs to replicated application configuration. */
@FunctionalInterface
public interface ManagedFilePolicy {
    boolean isManaged(Path relativePath);
}
