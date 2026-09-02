package nl.hauntedmc.featureframework.cluster;

import java.nio.file.Path;
import java.util.Objects;

/** Hash-addressed metadata for one managed configuration file. */
public record ConfigManifestFile(
        String path,
        String kind,
        String sha256,
        long size
) implements Comparable<ConfigManifestFile> {
    /** Maximum managed relative path length supported by the v1 MySQL control-plane schema. */
    public static final int MAX_PATH_LENGTH = 255;

    public ConfigManifestFile {
        path = normalizePath(path);
        kind = requireText(kind, "kind");
        sha256 = requireSha256(sha256);
        if (size < 0) throw new IllegalArgumentException("size must be non-negative");
    }

    @Override
    public int compareTo(ConfigManifestFile other) {
        return path.compareTo(Objects.requireNonNull(other, "other").path);
    }

    private static String normalizePath(String value) {
        String normalized = requireText(value, "path").replace('\\', '/');
        Path pathValue = Path.of(normalized);
        if (pathValue.isAbsolute() || normalized.startsWith("../") || normalized.contains("/../")) {
            throw new IllegalArgumentException("path must remain relative to the application data directory: " + value);
        }
        if (normalized.length() > MAX_PATH_LENGTH) {
            throw new IllegalArgumentException("path must be at most " + MAX_PATH_LENGTH + " characters");
        }
        return normalized;
    }

    private static String requireSha256(String value) {
        String normalized = requireText(value, "sha256").toLowerCase(java.util.Locale.ROOT);
        if (!normalized.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("sha256 must be 64 hexadecimal characters");
        return normalized;
    }

    private static String requireText(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " must not be blank");
        return normalized;
    }
}
