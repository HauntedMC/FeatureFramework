package nl.hauntedmc.featureframework.cluster;

import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/** Explicit allowlist plus safe FeatureFramework defaults for replicated files. */
public final class ManagedFileSet implements ManagedFilePolicy {
    private final Set<String> explicitPaths;
    private final Set<String> languageFiles;

    private ManagedFileSet(Builder builder) {
        explicitPaths = Set.copyOf(builder.explicitPaths);
        languageFiles = Set.copyOf(builder.languageFiles);
    }

    public static Builder builder() { return new Builder(); }

    public static ManagedFileSet defaults() { return builder().build(); }

    @Override
    public boolean isManaged(Path relativePath) {
        String path = normalize(relativePath);
        if (explicitPaths.contains(path)) return true;
        if ("config.yml".equals(path)) return true;
        String[] parts = path.split("/");
        if (parts.length != 3 || !"features".equals(parts[0])) return false;
        String file = parts[2].toLowerCase(Locale.ROOT);
        return "config.yml".equals(file) || "messages.yml".equals(file) || languageFiles.contains(file);
    }

    public Set<String> explicitPaths() { return explicitPaths; }
    public Set<String> languageFiles() { return languageFiles; }

    private static String normalize(Path path) {
        Objects.requireNonNull(path, "path");
        if (path.isAbsolute()) throw new IllegalArgumentException("Managed paths must be relative");
        String value = path.normalize().toString().replace('\\', '/');
        if (value.isBlank() || value.startsWith("../") || value.contains("/../")) {
            throw new IllegalArgumentException("Managed path escapes the application data directory: " + path);
        }
        return value;
    }

    public static final class Builder {
        private final Set<String> explicitPaths = new LinkedHashSet<>();
        private final Set<String> languageFiles = new LinkedHashSet<>();

        public Builder add(Path path) {
            explicitPaths.add(normalize(path));
            return this;
        }

        public Builder add(String path) { return add(Path.of(path)); }

        /** Adds one known feature-level language YAML file name, for example {@code nl.yml}. */
        public Builder languageFile(String fileName) {
            String normalized = Objects.requireNonNull(fileName, "fileName").trim().toLowerCase(Locale.ROOT);
            if (!normalized.matches("[a-z0-9_-]+\\.yml")) {
                throw new IllegalArgumentException("language file must be a simple .yml file name");
            }
            if ("config.yml".equals(normalized) || "messages.yml".equals(normalized)) return this;
            languageFiles.add(normalized);
            return this;
        }

        public ManagedFileSet build() { return new ManagedFileSet(this); }
    }
}
