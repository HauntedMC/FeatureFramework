package nl.hauntedmc.featureframework.host;

import nl.hauntedmc.featureframework.operation.reset.FeatureFileResetRequest;
import nl.hauntedmc.featureframework.operation.reset.MessageResetScope;

import nl.hauntedmc.featureframework.config.FeatureStoragePaths;
import nl.hauntedmc.featureframework.toolkit.io.config.ConfigService;
import nl.hauntedmc.featureframework.toolkit.log.FrameworkLogger;
import nl.hauntedmc.featureframework.toolkit.io.localization.Language;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.UUID;
import java.util.regex.Pattern;

/** Filesystem transaction support for feature config and localization reset operations. */
final class FeatureFileResetStorage {
    private static final String BACKUP_ROOT = "backups/feature-resets";
    private static final String MANIFEST = "manifest.properties";
    private static final Pattern MESSAGE_OVERRIDE = Pattern.compile("messages_[A-Za-z0-9_-]+\\.yml");
    private static final Pattern BACKUP_FILE = Pattern.compile("file-[0-9]+\\.bin");
    private static final DateTimeFormatter ID_TIME =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);
    private static final int RETAIN_COMPLETED = 5;

    private final ConfigService files;
    private final FrameworkLogger logger;
    private final Path dataDirectory;

    FeatureFileResetStorage(ConfigService files, FrameworkLogger logger) {
        this.files = Objects.requireNonNull(files, "files");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.dataDirectory = files.dataDirectory().toAbsolutePath().normalize();
    }

    List<String> targets(String feature, FeatureFileResetRequest request) throws IOException {
        List<String> targets = new ArrayList<>();
        if (request instanceof FeatureFileResetRequest.Config) {
            targets.add(FeatureStoragePaths.configPath(feature));
        } else if (request instanceof FeatureFileResetRequest.Messages messages) {
            targets.add(FeatureStoragePaths.messagesPath(feature));
            if (messages.scope() == MessageResetScope.MAIN_AND_OVERRIDES) {
                targets.addAll(messageOverrides(feature));
            }
        }
        for (String target : targets) validateTarget(target);
        return List.copyOf(targets);
    }

    Backup begin(String feature, FeatureFileResetRequest request) throws IOException {
        List<String> targets = targets(feature, request);
        List<String> entriesToBackup = new ArrayList<>(targets);
        for (String prerequisite : prerequisites(feature)) {
            if (!entriesToBackup.contains(prerequisite) && isMalformedExistingYaml(prerequisite)) {
                entriesToBackup.add(prerequisite);
            }
        }
        String id = ID_TIME.format(Instant.now()) + "-" + UUID.randomUUID().toString().substring(0, 8);
        String directoryPath = BACKUP_ROOT + "/" + feature + "/" + id;
        Path directory = safeResolve(directoryPath);
        ensureNoSymbolicLinks(directory);
        Files.createDirectories(directory);

        List<Entry> entries = new ArrayList<>();
        for (int index = 0; index < entriesToBackup.size(); index++) {
            String relative = entriesToBackup.get(index);
            Path source = validateTarget(relative);
            boolean existed = Files.exists(source, LinkOption.NOFOLLOW_LINKS);
            String backupName = "file-" + index + ".bin";
            String checksum = "";
            if (existed) {
                Path destination = directory.resolve(backupName);
                Files.copy(source, destination, StandardCopyOption.COPY_ATTRIBUTES);
                checksum = checksum(destination);
            }
            entries.add(new Entry(relative, existed, backupName, checksum, targets.contains(relative)));
        }
        Backup backup = new Backup(id, directory, entries);
        writeManifest(backup, "PREPARED");
        return backup;
    }

    List<String> stage(Backup backup, FeatureFileResetRequest request) throws IOException {
        List<String> deleted = new ArrayList<>();
        String main = backup.entries().stream().filter(Entry::target).findFirst().orElseThrow().relativePath();
        files.replaceWithEmptyDocument(main);
        if (request instanceof FeatureFileResetRequest.Messages messages
                && messages.scope() == MessageResetScope.MAIN_AND_OVERRIDES) {
            for (Entry entry : backup.entries()) {
                if (!entry.target() || entry.relativePath().equals(main)) continue;
                files.deleteOptional(entry.relativePath());
                deleted.add(Path.of(entry.relativePath()).getFileName().toString());
            }
        }
        return List.copyOf(deleted);
    }

    boolean stageMalformedPrerequisites(Backup backup) {
        boolean staged = false;
        for (Entry entry : backup.entries()) {
            if (entry.target()) continue;
            files.replaceWithEmptyDocument(entry.relativePath());
            staged = true;
        }
        return staged;
    }

    void restorePrerequisites(Backup backup) throws IOException {
        IOException failure = null;
        for (Entry entry : backup.entries()) {
            if (entry.target()) continue;
            try {
                restoreEntry(backup, entry);
            } catch (IOException entryFailure) {
                if (failure == null) failure = entryFailure;
                else failure.addSuppressed(entryFailure);
            }
        }
        if (failure != null) throw failure;
    }

    void restore(Backup backup) throws IOException {
        IOException failure = null;
        for (Entry entry : backup.entries()) {
            try {
                restoreEntry(backup, entry);
            } catch (IOException entryFailure) {
                if (failure == null) failure = entryFailure;
                else failure.addSuppressed(entryFailure);
            }
        }
        if (failure != null) throw failure;
    }

    private void restoreEntry(Backup backup, Entry entry) throws IOException {
        Path target = validateTarget(entry.relativePath(), false);
        if (entry.existed()) {
            Path source = backupSource(backup, entry);
            if (!checksum(source).equals(entry.checksum())) {
                throw new IOException("Backup checksum mismatch for " + entry.relativePath());
            }
            atomicCopy(source, target);
            try {
                files.reloadIfCached(entry.relativePath());
            } catch (RuntimeException invalidOriginal) {
                // Restoring an originally malformed document is still a successful byte restoration.
            }
        } else {
            files.deleteOptional(entry.relativePath());
        }
    }

    private Path backupSource(Backup backup, Entry entry) throws IOException {
        String backupName = entry.backupName();
        if (backupName == null || !BACKUP_FILE.matcher(backupName).matches()) {
            throw new IOException("Invalid file name in feature reset journal");
        }
        Path directory = backup.directory().toAbsolutePath().normalize();
        Path source = directory.resolve(backupName).normalize();
        if (!source.getParent().equals(directory)) {
            throw new IOException("Backup file escapes its transaction directory");
        }
        ensureNoSymbolicLinks(source);
        if (!Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Feature reset backup file is missing or unsafe: " + backupName);
        }
        return source;
    }

    void commit(Backup backup) throws IOException {
        writeManifest(backup, "COMMITTED");
        prune(backup.directory().getParent());
    }

    void markRolledBack(Backup backup) throws IOException {
        writeManifest(backup, "ROLLED_BACK");
        prune(backup.directory().getParent());
    }

    void recoverIncompleteTransactions() {
        Path root = files.resolve(BACKUP_ROOT);
        if (Files.notExists(root, LinkOption.NOFOLLOW_LINKS)) return;
        try {
            ensureNoSymbolicLinks(root);
        } catch (IOException failure) {
            throw new IllegalStateException("Unsafe feature reset backup directory", failure);
        }
        try (var features = Files.newDirectoryStream(root)) {
            for (Path featureDirectory : features) {
                if (!Files.isDirectory(featureDirectory, LinkOption.NOFOLLOW_LINKS)) continue;
                try (var attempts = Files.newDirectoryStream(featureDirectory)) {
                    for (Path attempt : attempts) recoverIfPrepared(attempt);
                }
            }
        } catch (IOException failure) {
            throw new IllegalStateException("Could not recover incomplete feature file resets", failure);
        }
    }

    private void recoverIfPrepared(Path directory) throws IOException {
        ensureNoSymbolicLinks(directory);
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) return;
        Path manifest = directory.resolve(MANIFEST);
        if (!Files.isRegularFile(manifest, LinkOption.NOFOLLOW_LINKS)) return;
        Properties values = loadProperties(manifest);
        if (!"PREPARED".equals(values.getProperty("state"))) return;
        Backup backup = parseBackup(directory, values);
        logger.warn("Recovering incomplete feature file reset '" + backup.id() + "'.");
        restore(backup);
        writeManifest(backup, "RECOVERED");
    }

    private List<String> messageOverrides(String feature) throws IOException {
        String directoryRelative = FeatureStoragePaths.featureDirectory(feature);
        Path directory = safeResolve(directoryRelative);
        if (Files.notExists(directory, LinkOption.NOFOLLOW_LINKS)) return List.of();
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            throw new UnsafeTargetException("Feature storage is not a directory: " + directoryRelative);
        }
        List<String> matches = new ArrayList<>();
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(directory)) {
            for (Path entry : entries) {
                String name = entry.getFileName().toString();
                if (!MESSAGE_OVERRIDE.matcher(name).matches()) continue;
                String relative = directoryRelative + "/" + name;
                validateTarget(relative);
                matches.add(relative);
            }
        }
        matches.sort(String.CASE_INSENSITIVE_ORDER);
        return matches;
    }

    private static List<String> prerequisites(String feature) {
        List<String> paths = new ArrayList<>();
        paths.add(FeatureStoragePaths.configPath(feature));
        paths.add(FeatureStoragePaths.messagesPath(feature));
        for (Language language : Language.localizableValues()) {
            paths.add(FeatureStoragePaths.messagesPath(feature, language));
        }
        return paths;
    }

    private boolean isMalformedExistingYaml(String relative) throws IOException {
        Path path = validateTarget(relative);
        if (Files.notExists(path, LinkOption.NOFOLLOW_LINKS)) return false;
        try {
            YamlConfigurationLoader.builder().path(path).build().load();
            return false;
        } catch (IOException failure) {
            return true;
        }
    }

    private Path validateTarget(String relative) throws IOException {
        return validateTarget(relative, true);
    }

    private Path validateTarget(String relative, boolean requireRegularWhenPresent) throws IOException {
        Path target = safeResolve(relative);
        ensureNoSymbolicLinks(target);
        if (requireRegularWhenPresent && Files.exists(target, LinkOption.NOFOLLOW_LINKS)
                && !Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new UnsafeTargetException("Feature storage target is not a regular file: " + relative);
        }
        return target;
    }

    private void ensureNoSymbolicLinks(Path target) throws IOException {
        Path cursor = dataDirectory;
        Path relativePath = dataDirectory.relativize(target.toAbsolutePath().normalize());
        for (Path segment : relativePath) {
            cursor = cursor.resolve(segment);
            if (Files.exists(cursor, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(cursor)) {
                throw new UnsafeTargetException("Symbolic links are not allowed in feature reset storage");
            }
        }
    }

    private Path safeResolve(String relative) {
        Path resolved = files.resolve(relative).toAbsolutePath().normalize();
        if (!resolved.startsWith(dataDirectory)) {
            throw new UnsafeTargetException("Feature reset target escapes the data directory");
        }
        return resolved;
    }

    private void writeManifest(Backup backup, String state) throws IOException {
        Properties values = new Properties();
        values.setProperty("version", "1");
        values.setProperty("state", state);
        values.setProperty("id", backup.id());
        values.setProperty("count", Integer.toString(backup.entries().size()));
        for (int index = 0; index < backup.entries().size(); index++) {
            Entry entry = backup.entries().get(index);
            values.setProperty("entry." + index + ".path", encode(entry.relativePath()));
            values.setProperty("entry." + index + ".existed", Boolean.toString(entry.existed()));
            values.setProperty("entry." + index + ".backup", entry.backupName());
            values.setProperty("entry." + index + ".sha256", entry.checksum());
            values.setProperty("entry." + index + ".target", Boolean.toString(entry.target()));
        }
        Path manifest = backup.directory().resolve(MANIFEST);
        Path temporary = Files.createTempFile(backup.directory(), ".manifest-", ".tmp");
        try (OutputStream output = Files.newOutputStream(temporary)) {
            values.store(output, "FeatureFramework feature file reset journal");
        }
        try {
            Files.move(temporary, manifest, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, manifest, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private Backup parseBackup(Path directory, Properties values) throws IOException {
        if (!"1".equals(values.getProperty("version"))) {
            throw new IOException("Unsupported feature reset journal version");
        }
        int count = Integer.parseInt(values.getProperty("count", "-1"));
        if (count < 1) throw new IOException("Invalid feature reset journal entry count");
        List<Entry> entries = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            entries.add(new Entry(
                    decode(values.getProperty("entry." + index + ".path")),
                    Boolean.parseBoolean(values.getProperty("entry." + index + ".existed")),
                    values.getProperty("entry." + index + ".backup"),
                    values.getProperty("entry." + index + ".sha256", ""),
                    Boolean.parseBoolean(values.getProperty("entry." + index + ".target", "true"))
            ));
        }
        return new Backup(values.getProperty("id", directory.getFileName().toString()), directory, entries);
    }

    private static Properties loadProperties(Path path) throws IOException {
        Properties values = new Properties();
        try (InputStream input = Files.newInputStream(path)) {
            values.load(input);
        }
        return values;
    }

    private void prune(Path featureBackupDirectory) {
        try (var attempts = Files.list(featureBackupDirectory)) {
            List<Path> completed = attempts
                    .filter(path -> Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))
                    .filter(this::completed)
                    .sorted(Comparator.comparing((Path path) -> path.getFileName().toString()).reversed())
                    .toList();
            for (Path stale : completed.stream().skip(RETAIN_COMPLETED).toList()) deleteTree(stale);
        } catch (IOException failure) {
            logger.warn("Could not prune old feature reset backups.", failure);
        }
    }

    private boolean completed(Path directory) {
        try {
            String state = loadProperties(directory.resolve(MANIFEST)).getProperty("state");
            return "COMMITTED".equals(state) || "RECOVERED".equals(state) || "ROLLED_BACK".equals(state);
        } catch (IOException ignored) {
            return false;
        }
    }

    private static void deleteTree(Path directory) throws IOException {
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }

    private static void atomicCopy(Path source, Path target) throws IOException {
        Files.createDirectories(target.getParent());
        Path temporary = Files.createTempFile(target.getParent(), ".restore-", ".tmp");
        try {
            Files.copy(source, temporary, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static String checksum(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(path)) {
                byte[] buffer = new byte[8192];
                for (int read; (read = input.read(buffer)) >= 0;) digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static String decode(String value) throws IOException {
        try {
            return new String(Base64.getUrlDecoder().decode(value), java.nio.charset.StandardCharsets.UTF_8);
        } catch (RuntimeException failure) {
            throw new IOException("Invalid path in feature reset journal", failure);
        }
    }

    record Backup(String id, Path directory, List<Entry> entries) {
        Backup {
            entries = List.copyOf(entries);
        }
    }

    private record Entry(String relativePath, boolean existed, String backupName, String checksum, boolean target) { }

    static final class UnsafeTargetException extends IllegalArgumentException {
        private static final long serialVersionUID = 1L;

        UnsafeTargetException(String message) { super(message); }
    }
}
