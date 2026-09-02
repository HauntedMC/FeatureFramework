package nl.hauntedmc.featureframework.cluster;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.OptionalLong;

/** Immutable manifest describing one published configuration generation. */
public record ConfigManifest(
        int protocolVersion,
        ReplicaGroupIdentity group,
        long generation,
        String publisherNode,
        String publisherBootId,
        long fencingToken,
        String applicationVersion,
        String configCompatibilityVersion,
        Instant createdAt,
        OptionalLong sourceGeneration,
        List<ConfigManifestFile> files,
        String manifestHash
) {
    public static final int CURRENT_PROTOCOL_VERSION = 1;

    public ConfigManifest {
        if (protocolVersion <= 0) throw new IllegalArgumentException("protocolVersion must be positive");
        group = Objects.requireNonNull(group, "group");
        if (generation <= 0) throw new IllegalArgumentException("generation must be positive");
        publisherNode = text(publisherNode, "publisherNode");
        publisherBootId = text(publisherBootId, "publisherBootId");
        if (fencingToken <= 0) throw new IllegalArgumentException("fencingToken must be positive");
        applicationVersion = text(applicationVersion, "applicationVersion");
        configCompatibilityVersion = text(configCompatibilityVersion, "configCompatibilityVersion");
        createdAt = Objects.requireNonNull(createdAt, "createdAt");
        sourceGeneration = sourceGeneration == null ? OptionalLong.empty() : sourceGeneration;
        if (sourceGeneration.isPresent() && sourceGeneration.getAsLong() <= 0) {
            throw new IllegalArgumentException("sourceGeneration must be positive when present");
        }
        files = files == null ? List.of() : files.stream().sorted(Comparator.naturalOrder()).toList();
        manifestHash = sha(manifestHash);
    }

    private static String text(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " must not be blank");
        return normalized;
    }

    private static String sha(String value) {
        String normalized = text(value, "manifestHash").toLowerCase(java.util.Locale.ROOT);
        if (!normalized.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("manifestHash must be a SHA-256 hash");
        return normalized;
    }
}
