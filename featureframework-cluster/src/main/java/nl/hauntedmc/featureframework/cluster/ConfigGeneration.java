package nl.hauntedmc.featureframework.cluster;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Immutable configuration contents paired with their verified manifest. */
public final class ConfigGeneration {
    private final ConfigManifest manifest;
    private final Map<String, byte[]> files;

    public ConfigGeneration(ConfigManifest manifest, Map<String, byte[]> files) {
        this.manifest = Objects.requireNonNull(manifest, "manifest");
        Map<String, byte[]> copy = new LinkedHashMap<>();
        Objects.requireNonNull(files, "files").forEach((path, contents) ->
                copy.put(Objects.requireNonNull(path, "path"), Objects.requireNonNull(contents, "contents").clone()));
        this.files = Map.copyOf(copy);
        verify();
    }

    public ConfigManifest manifest() { return manifest; }

    public Map<String, byte[]> files() {
        Map<String, byte[]> copy = new LinkedHashMap<>();
        files.forEach((path, contents) -> copy.put(path, contents.clone()));
        return Map.copyOf(copy);
    }

    public byte[] file(String path) {
        byte[] contents = files.get(path);
        if (contents == null) throw new IllegalArgumentException("Generation does not contain managed file: " + path);
        return contents.clone();
    }

    public void verify() {
        if (files.size() != manifest.files().size()) {
            throw new IllegalArgumentException("Manifest/file count mismatch");
        }
        for (ConfigManifestFile file : manifest.files()) {
            byte[] contents = files.get(file.path());
            if (contents == null) throw new IllegalArgumentException("Missing manifest file: " + file.path());
            if (contents.length != file.size()) throw new IllegalArgumentException("Size mismatch for " + file.path());
            String hash = ConfigHashes.sha256(contents);
            if (!hash.equals(file.sha256())) throw new IllegalArgumentException("Hash mismatch for " + file.path());
        }
        String expected = ConfigHashes.manifestHash(manifest.files());
        if (!expected.equals(manifest.manifestHash())) throw new IllegalArgumentException("Manifest hash mismatch");
    }
}
