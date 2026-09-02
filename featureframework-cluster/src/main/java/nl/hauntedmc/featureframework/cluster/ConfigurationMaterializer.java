package nl.hauntedmc.featureframework.cluster;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Stages, verifies and atomically materializes managed configuration beneath one application data directory. */
public final class ConfigurationMaterializer {
    private final Path dataDirectory;
    private final Path replicaDirectory;
    private final ManagedFilePolicy managedFiles;

    public ConfigurationMaterializer(Path dataDirectory, ManagedFilePolicy managedFiles) {
        this.dataDirectory = dataDirectory.toAbsolutePath().normalize();
        this.replicaDirectory = this.dataDirectory.resolve(".replica");
        this.managedFiles = java.util.Objects.requireNonNull(managedFiles, "managedFiles");
    }

    public Map<String, byte[]> snapshot() {
        Map<String, byte[]> snapshot = new LinkedHashMap<>();
        if (Files.notExists(dataDirectory)) return Map.of();
        try (var paths = Files.walk(dataDirectory)) {
            for (Path file : paths.filter(Files::isRegularFile).toList()) {
                if (file.startsWith(replicaDirectory)) continue;
                Path relative = dataDirectory.relativize(file);
                if (managedFiles.isManaged(relative)) snapshot.put(normalize(relative), Files.readAllBytes(file));
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to snapshot managed configuration", exception);
        }
        return Map.copyOf(snapshot);
    }

    public boolean matches(ConfigGeneration generation) {
        generation.verify();
        Map<String, byte[]> local = snapshot();
        if (!local.keySet().equals(generation.files().keySet())) return false;
        for (Map.Entry<String, byte[]> entry : local.entrySet()) {
            if (!java.util.Arrays.equals(entry.getValue(), generation.file(entry.getKey()))) return false;
        }
        return true;
    }

    /** Backs up any local drift before restoring the authoritative generation. */
    public Path backupDrift() {
        Map<String, byte[]> current = snapshot();
        Path backup = replicaDirectory.resolve("drift").resolve(Instant.now().toEpochMilli() + "-" + java.util.UUID.randomUUID());
        try {
            for (Map.Entry<String, byte[]> entry : current.entrySet()) {
                Path target = backup.resolve(entry.getKey()).normalize();
                if (!target.startsWith(backup)) throw new IllegalStateException("Managed drift path escapes backup directory");
                Files.createDirectories(target.getParent());
                Files.write(target, entry.getValue());
            }
            return backup;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to back up replica filesystem drift", exception);
        }
    }

    public void materialize(ConfigGeneration generation) {
        generation.verify();
        Path stage = replicaDirectory.resolve("staging").resolve(
                "apply-" + generation.manifest().generation() + "-" + java.util.UUID.randomUUID());
        try {
            Files.createDirectories(stage);
            for (Map.Entry<String, byte[]> entry : generation.files().entrySet()) {
                Path relative = Path.of(entry.getKey());
                if (!managedFiles.isManaged(relative)) {
                    throw new IllegalArgumentException("Generation contains unmanaged path: " + entry.getKey());
                }
                Path target = stage.resolve(entry.getKey()).normalize();
                if (!target.startsWith(stage)) throw new IllegalArgumentException("Generation path escapes staging directory");
                Files.createDirectories(target.getParent());
                Files.write(target, entry.getValue());
            }

            Set<String> desired = generation.files().keySet();
            Set<String> existing = new LinkedHashSet<>(snapshot().keySet());
            for (String obsolete : existing) {
                if (!desired.contains(obsolete)) Files.deleteIfExists(dataDirectory.resolve(obsolete).normalize());
            }
            for (String path : desired) {
                Path source = stage.resolve(path).normalize();
                Path target = dataDirectory.resolve(path).normalize();
                if (!target.startsWith(dataDirectory)) throw new IllegalArgumentException("Generation path escapes data directory");
                Files.createDirectories(target.getParent());
                Path temp = Files.createTempFile(target.getParent(), "." + target.getFileName(), ".replica");
                try {
                    Files.copy(source, temp, StandardCopyOption.REPLACE_EXISTING);
                    try { Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
                    catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                        Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
                    }
                } finally { Files.deleteIfExists(temp); }
            }
            if (!matches(generation)) throw new IllegalStateException("Materialized generation did not verify on disk");
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to materialize replica configuration", exception);
        } finally {
            try { LastKnownGoodStore.deleteRecursively(stage); } catch (IOException ignored) { }
        }
    }

    private static String normalize(Path path) { return path.toString().replace('\\', '/'); }
}
