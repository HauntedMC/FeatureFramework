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

/** Stages, verifies and transactionally materializes managed configuration beneath one application data directory. */
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
        return immutableCopy(snapshot);
    }

    public boolean matches(ConfigGeneration generation) {
        generation.verify();
        return matchesSnapshot(generation.files());
    }

    /** Backs up the complete current managed snapshot before authoritative drift repair. */
    public Path backupDrift() {
        Map<String, byte[]> current = snapshot();
        Path backup = replicaDirectory.resolve("drift").resolve(
                Instant.now().toEpochMilli() + "-" + java.util.UUID.randomUUID());
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

    /**
     * Applies one verified generation. Multi-file filesystems cannot provide a group rename, so the
     * previous managed snapshot is retained in memory and restored if any move, delete or final
     * verification fails. The host only reloads after this method returns successfully.
     */
    public void materialize(ConfigGeneration generation) {
        generation.verify();
        Path stage = replicaDirectory.resolve("staging").resolve(
                "apply-" + generation.manifest().generation() + "-" + java.util.UUID.randomUUID());
        Map<String, byte[]> previous = snapshot();
        try {
            stageGeneration(stage, generation);
            applyStaged(stage, generation.files().keySet());
            if (!matches(generation)) throw new IllegalStateException("Materialized generation did not verify on disk");
        } catch (Throwable failure) {
            try {
                restoreSnapshot(previous);
                if (!matchesSnapshot(previous)) {
                    throw new IllegalStateException("Previous managed configuration did not verify after rollback");
                }
            } catch (Throwable rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }
            if (failure instanceof RuntimeException runtimeFailure) throw runtimeFailure;
            throw new IllegalStateException("Failed to materialize replica configuration", failure);
        } finally {
            try { LastKnownGoodStore.deleteRecursively(stage); } catch (IOException ignored) { }
        }
    }

    private void stageGeneration(Path stage, ConfigGeneration generation) throws IOException {
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
        for (ConfigManifestFile file : generation.manifest().files()) {
            Path staged = stage.resolve(file.path()).normalize();
            byte[] bytes = Files.readAllBytes(staged);
            if (bytes.length != file.size() || !ConfigHashes.sha256(bytes).equals(file.sha256())) {
                throw new IllegalStateException("Staged generation verification failed for " + file.path());
            }
        }
    }

    private void applyStaged(Path stage, Set<String> desired) throws IOException {
        Set<String> existing = new LinkedHashSet<>(snapshot().keySet());
        Map<String, Path> prepared = new LinkedHashMap<>();
        try {
            for (String path : desired) {
                Path target = target(path);
                Files.createDirectories(target.getParent());
                Path temp = Files.createTempFile(target.getParent(), "." + target.getFileName(), ".replica");
                Files.copy(stage.resolve(path).normalize(), temp, StandardCopyOption.REPLACE_EXISTING);
                prepared.put(path, temp);
            }
            for (String obsolete : existing) {
                if (!desired.contains(obsolete)) Files.deleteIfExists(target(obsolete));
            }
            for (Map.Entry<String, Path> entry : prepared.entrySet()) {
                atomicReplace(entry.getValue(), target(entry.getKey()));
            }
        } finally {
            for (Path temp : prepared.values()) Files.deleteIfExists(temp);
        }
    }

    private void restoreSnapshot(Map<String, byte[]> previous) throws IOException {
        Set<String> existing = new LinkedHashSet<>(snapshot().keySet());
        Map<String, Path> prepared = new LinkedHashMap<>();
        try {
            for (Map.Entry<String, byte[]> entry : previous.entrySet()) {
                Path target = target(entry.getKey());
                Files.createDirectories(target.getParent());
                Path temp = Files.createTempFile(target.getParent(), "." + target.getFileName(), ".rollback");
                Files.write(temp, entry.getValue());
                prepared.put(entry.getKey(), temp);
            }
            for (String obsolete : existing) {
                if (!previous.containsKey(obsolete)) Files.deleteIfExists(target(obsolete));
            }
            for (Map.Entry<String, Path> entry : prepared.entrySet()) {
                atomicReplace(entry.getValue(), target(entry.getKey()));
            }
        } finally {
            for (Path temp : prepared.values()) Files.deleteIfExists(temp);
        }
    }

    private boolean matchesSnapshot(Map<String, byte[]> expected) {
        Map<String, byte[]> local = snapshot();
        if (!local.keySet().equals(expected.keySet())) return false;
        for (Map.Entry<String, byte[]> entry : local.entrySet()) {
            if (!java.util.Arrays.equals(entry.getValue(), expected.get(entry.getKey()))) return false;
        }
        return true;
    }

    private Path target(String relativePath) {
        Path target = dataDirectory.resolve(relativePath).normalize();
        if (!target.startsWith(dataDirectory)) throw new IllegalArgumentException("Generation path escapes data directory");
        if (!managedFiles.isManaged(dataDirectory.relativize(target))) {
            throw new IllegalArgumentException("Generation contains unmanaged path: " + relativePath);
        }
        return target;
    }

    private static void atomicReplace(Path source, Path target) throws IOException {
        try { Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static Map<String, byte[]> immutableCopy(Map<String, byte[]> source) {
        Map<String, byte[]> copy = new LinkedHashMap<>();
        source.forEach((path, bytes) -> copy.put(path, bytes.clone()));
        return Map.copyOf(copy);
    }

    private static String normalize(Path path) { return path.toString().replace('\\', '/'); }
}