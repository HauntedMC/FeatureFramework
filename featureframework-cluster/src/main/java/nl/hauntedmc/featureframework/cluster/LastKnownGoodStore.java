package nl.hauntedmc.featureframework.cluster;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Properties;

/** Verified local last-known-good store beneath {@code .replica/}. */
public final class LastKnownGoodStore {
    private final Path root;

    public LastKnownGoodStore(Path dataDirectory) {
        root = dataDirectory.toAbsolutePath().normalize().resolve(".replica");
    }

    public Path root() { return root; }

    public synchronized void save(ConfigGeneration generation) {
        generation.verify();
        Path target = root.resolve("generations").resolve(Long.toString(generation.manifest().generation()));
        Path staging = root.resolve("staging").resolve("lkg-" + generation.manifest().generation() + "-" + java.util.UUID.randomUUID());
        try {
            Files.createDirectories(staging);
            Properties properties = manifestProperties(generation.manifest());
            try (OutputStream output = Files.newOutputStream(staging.resolve("manifest.properties"))) {
                properties.store(output, "FeatureFramework replica generation");
            }
            Path filesRoot = staging.resolve("files");
            for (Map.Entry<String, byte[]> entry : generation.files().entrySet()) {
                Path file = filesRoot.resolve(entry.getKey()).normalize();
                if (!file.startsWith(filesRoot)) throw new IllegalArgumentException("Generation path escapes LKG store");
                Files.createDirectories(file.getParent());
                Files.write(file, entry.getValue());
            }
            Files.createDirectories(target.getParent());
            deleteRecursively(target);
            moveDirectory(staging, target);
            Files.createDirectories(root);
            String state = "{\n  \"generation\": " + generation.manifest().generation() + ",\n"
                    + "  \"manifestHash\": \"" + generation.manifest().manifestHash() + "\",\n"
                    + "  \"configCompatibilityVersion\": \""
                    + escape(generation.manifest().configCompatibilityVersion()) + "\"\n}\n";
            atomicWrite(root.resolve("state.json"), state.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to persist replica last-known-good generation", exception);
        } finally {
            try { deleteRecursively(staging); } catch (IOException ignored) { }
        }
    }

    public synchronized Optional<ConfigGeneration> load() {
        Path state = root.resolve("state.json");
        if (Files.notExists(state)) return Optional.empty();
        try {
            String json = Files.readString(state);
            long generation = Long.parseLong(extractJson(json, "generation"));
            Path directory = root.resolve("generations").resolve(Long.toString(generation));
            ConfigGeneration loaded = readGeneration(directory);
            String expectedHash = extractJson(json, "manifestHash");
            if (!expectedHash.equals(loaded.manifest().manifestHash())) {
                throw new IllegalStateException("LKG state hash does not match generation manifest");
            }
            loaded.verify();
            return Optional.of(loaded);
        } catch (IOException | RuntimeException exception) {
            throw new IllegalStateException("Failed to load replica last-known-good generation", exception);
        }
    }

    private static ConfigGeneration readGeneration(Path directory) throws IOException {
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(directory.resolve("manifest.properties"))) {
            properties.load(input);
        }
        ReplicaGroupIdentity group = new ReplicaGroupIdentity(
                required(properties, "namespace"), required(properties, "applicationId"),
                required(properties, "groupId"), required(properties, "configuredLeader"));
        int count = Integer.parseInt(required(properties, "file.count"));
        List<ConfigManifestFile> manifestFiles = new ArrayList<>();
        Map<String, byte[]> files = new LinkedHashMap<>();
        Path filesRoot = directory.resolve("files");
        for (int index = 0; index < count; index++) {
            String prefix = "file." + index + ".";
            String path = required(properties, prefix + "path");
            ConfigManifestFile file = new ConfigManifestFile(path, required(properties, prefix + "kind"),
                    required(properties, prefix + "sha256"), Long.parseLong(required(properties, prefix + "size")));
            manifestFiles.add(file);
            Path physical = filesRoot.resolve(path).normalize();
            if (!physical.startsWith(filesRoot)) throw new IllegalStateException("Stored LKG path escapes generation");
            files.put(path, Files.readAllBytes(physical));
        }
        String source = properties.getProperty("sourceGeneration", "").trim();
        ConfigManifest manifest = new ConfigManifest(
                Integer.parseInt(required(properties, "protocolVersion")), group,
                Long.parseLong(required(properties, "generation")), required(properties, "publisherNode"),
                required(properties, "publisherBootId"), Long.parseLong(required(properties, "fencingToken")),
                required(properties, "applicationVersion"), required(properties, "configCompatibilityVersion"),
                Instant.parse(required(properties, "createdAt")),
                source.isEmpty() ? OptionalLong.empty() : OptionalLong.of(Long.parseLong(source)),
                manifestFiles, required(properties, "manifestHash"));
        return new ConfigGeneration(manifest, files);
    }

    private static Properties manifestProperties(ConfigManifest manifest) {
        Properties properties = new Properties();
        properties.setProperty("protocolVersion", Integer.toString(manifest.protocolVersion()));
        properties.setProperty("namespace", manifest.group().namespace());
        properties.setProperty("applicationId", manifest.group().applicationId());
        properties.setProperty("groupId", manifest.group().groupId());
        properties.setProperty("configuredLeader", manifest.group().configuredLeader());
        properties.setProperty("generation", Long.toString(manifest.generation()));
        properties.setProperty("publisherNode", manifest.publisherNode());
        properties.setProperty("publisherBootId", manifest.publisherBootId());
        properties.setProperty("fencingToken", Long.toString(manifest.fencingToken()));
        properties.setProperty("applicationVersion", manifest.applicationVersion());
        properties.setProperty("configCompatibilityVersion", manifest.configCompatibilityVersion());
        properties.setProperty("createdAt", manifest.createdAt().toString());
        if (manifest.sourceGeneration().isPresent()) {
            properties.setProperty("sourceGeneration", Long.toString(manifest.sourceGeneration().getAsLong()));
        }
        properties.setProperty("manifestHash", manifest.manifestHash());
        properties.setProperty("file.count", Integer.toString(manifest.files().size()));
        for (int index = 0; index < manifest.files().size(); index++) {
            ConfigManifestFile file = manifest.files().get(index);
            String prefix = "file." + index + ".";
            properties.setProperty(prefix + "path", file.path());
            properties.setProperty(prefix + "kind", file.kind());
            properties.setProperty(prefix + "sha256", file.sha256());
            properties.setProperty(prefix + "size", Long.toString(file.size()));
        }
        return properties;
    }

    private static String required(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) throw new IllegalStateException("Missing generation property: " + key);
        return value.trim();
    }

    private static String extractJson(String json, String key) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(
                "\\\"" + java.util.regex.Pattern.quote(key) + "\\\"\\s*:\\s*(?:\\\"([^\\\"]*)\\\"|([0-9]+))")
                .matcher(json);
        if (!matcher.find()) throw new IllegalStateException("Missing LKG state field: " + key);
        return matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
    }

    private static String escape(String value) { return value.replace("\\", "\\\\").replace("\"", "\\\""); }

    private static void atomicWrite(Path target, byte[] bytes) throws IOException {
        Files.createDirectories(target.getParent());
        Path temp = Files.createTempFile(target.getParent(), "." + target.getFileName(), ".tmp");
        try {
            Files.write(temp, bytes);
            try { Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally { Files.deleteIfExists(temp); }
    }

    private static void moveDirectory(Path source, Path target) throws IOException {
        try { Files.move(source, target, StandardCopyOption.ATOMIC_MOVE); }
        catch (java.nio.file.AtomicMoveNotSupportedException ignored) { Files.move(source, target); }
    }

    static void deleteRecursively(Path path) throws IOException {
        if (path == null || Files.notExists(path)) return;
        try (var stream = Files.walk(path)) {
            for (Path entry : stream.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(entry);
        }
    }
}
