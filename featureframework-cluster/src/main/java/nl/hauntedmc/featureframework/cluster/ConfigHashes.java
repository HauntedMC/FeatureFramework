package nl.hauntedmc.featureframework.cluster;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/** Deterministic SHA-256 helpers for replica configuration manifests. */
public final class ConfigHashes {
    private ConfigHashes() { }

    public static String sha256(byte[] contents) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(contents));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    public static String manifestHash(List<ConfigManifestFile> files) {
        StringBuilder canonical = new StringBuilder();
        files.stream().sorted().forEach(file -> canonical
                .append(file.path()).append('\n')
                .append(file.kind()).append('\n')
                .append(file.sha256()).append('\n')
                .append(file.size()).append('\n'));
        return sha256(canonical.toString().getBytes(StandardCharsets.UTF_8));
    }
}
