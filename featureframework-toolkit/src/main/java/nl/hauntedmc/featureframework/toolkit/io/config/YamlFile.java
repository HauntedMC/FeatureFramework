package nl.hauntedmc.featureframework.toolkit.io.config;

import nl.hauntedmc.featureframework.toolkit.log.FrameworkLogger;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurationOptions;
import org.spongepowered.configurate.yaml.NodeStyle;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;

/** Owns one YAML file, its last-known-good in-memory tree, and its synchronization boundary. */
public final class YamlFile {
    private final Path path;
    private final Path policyPath;
    private final FrameworkLogger logger;
    private final ConfigMutationPolicy mutationPolicy;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final YamlConfigurationLoader loader;
    private volatile CommentedConfigurationNode root;
    private volatile ConfigLoadException loadFailure;

    public YamlFile(Path path, FrameworkLogger logger) {
        this(path, path.getFileName(), logger, ConfigMutationPolicy.allowAll());
    }

    YamlFile(Path path, Path policyPath, FrameworkLogger logger, ConfigMutationPolicy mutationPolicy) {
        this.path = Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
        this.policyPath = Objects.requireNonNull(policyPath, "policyPath");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.mutationPolicy = Objects.requireNonNull(mutationPolicy, "mutationPolicy");
        this.loader = loaderFor(this.path);
        reload();
    }

    public YamlFile(Path path, java.util.logging.Logger logger) { this(path, FrameworkLogger.from(logger)); }
    public YamlFile(Path path, org.slf4j.Logger logger) { this(path, FrameworkLogger.from(logger)); }

    public Path path() { return path; }
    public Optional<ConfigLoadException> loadFailure() { return Optional.ofNullable(loadFailure); }
    public boolean hasLoadFailure() { return loadFailure != null; }
    public ReentrantReadWriteLock lock() { return lock; }

    public void reload() {
        lock.writeLock().lock();
        try {
            root = loader.load();
            loadFailure = null;
        } catch (IOException exception) {
            logger.error("[FeatureFramework] Could not load YAML '" + path + "'.", exception);
            ConfigLoadException failure = new ConfigLoadException(path, exception);
            loadFailure = failure;
            throw failure;
        } finally { lock.writeLock().unlock(); }
    }

    public void mutateAndSave(Consumer<CommentedConfigurationNode> mutator) {
        Objects.requireNonNull(mutator, "mutator");
        mutationPolicy.checkMutation(policyPath, "mutate and save");
        lock.writeLock().lock();
        try {
            CommentedConfigurationNode candidate = copyRootUnsafe();
            mutator.accept(candidate);
            commitCandidateUnsafe(candidate);
        } finally { lock.writeLock().unlock(); }
    }

    public void replaceWithEmptyDocument() {
        mutationPolicy.checkMutation(policyPath, "replace with empty document");
        lock.writeLock().lock();
        try {
            CommentedConfigurationNode candidate = CommentedConfigurationNode.root();
            saveCandidate(candidate, true);
            root = candidate;
            loadFailure = null;
        } finally { lock.writeLock().unlock(); }
    }

    Object getRaw(String absolutePath) {
        lock.readLock().lock();
        try {
            if (absolutePath == null || absolutePath.isBlank()) return root.get(Object.class);
            return root.node(splitPath(absolutePath)).get(Object.class);
        } catch (Exception ignored) { return null; }
        finally { lock.readLock().unlock(); }
    }

    void setRawAndSave(String absolutePath, Object value) {
        mutationPolicy.checkMutation(policyPath, "update '" + absolutePath + "'");
        lock.writeLock().lock();
        try {
            CommentedConfigurationNode candidate = copyRootUnsafe();
            if (absolutePath == null || absolutePath.isBlank()) candidate.set(value);
            else candidate.node(splitPath(absolutePath)).set(value);
            commitCandidateUnsafe(candidate);
        } catch (ConfigPersistenceException exception) { throw exception; }
        catch (Exception exception) {
            throw new ConfigPersistenceException(path, "update '" + absolutePath + "' in", exception);
        } finally { lock.writeLock().unlock(); }
    }

    CommentedConfigurationNode copyRootUnsafe() { return root.copy(); }

    void commitCandidateUnsafe(CommentedConfigurationNode candidate) {
        Objects.requireNonNull(candidate, "candidate");
        mutationPolicy.checkMutation(policyPath, "save");
        saveCandidate(candidate, false);
        root = candidate;
    }

    private void saveCandidate(CommentedConfigurationNode candidate, boolean allowInvalidReplacement) {
        ConfigLoadException currentFailure = loadFailure;
        if (currentFailure != null && !allowInvalidReplacement) {
            throw new ConfigPersistenceException(path, "save while the latest disk version is invalid for", currentFailure);
        }
        Path temporary = null;
        try {
            Files.createDirectories(path.getParent());
            temporary = Files.createTempFile(path.getParent(), "." + path.getFileName(), ".tmp");
            loaderFor(temporary).save(candidate);
            preservePosixPermissions(path, temporary);
            try { Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            logger.error("[FeatureFramework] Could not save YAML '" + path + "'.", exception);
            throw new ConfigPersistenceException(path, "save", exception);
        } finally {
            if (temporary != null) {
                try { Files.deleteIfExists(temporary); }
                catch (IOException cleanupFailure) {
                    logger.warn("[FeatureFramework] Could not remove temporary YAML '" + temporary + "'.", cleanupFailure);
                }
            }
        }
    }

    private static void preservePosixPermissions(Path source, Path target) {
        if (Files.notExists(source)) return;
        try {
            Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(source);
            Files.setPosixFilePermissions(target, permissions);
        } catch (IOException | UnsupportedOperationException ignored) { }
    }

    private static YamlConfigurationLoader loaderFor(Path target) {
        return YamlConfigurationLoader.builder().path(target).nodeStyle(NodeStyle.BLOCK)
                .defaultOptions(ConfigurationOptions.defaults()).build();
    }

    static Object[] splitPath(String dotted) {
        if (dotted == null || dotted.isBlank()) return new Object[0];
        String[] parts = dotted.split("\\.");
        Object[] output = new Object[parts.length];
        System.arraycopy(parts, 0, output, 0, parts.length);
        return output;
    }
}
