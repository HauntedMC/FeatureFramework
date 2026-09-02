package nl.hauntedmc.featureframework.toolkit.io.config;

import nl.hauntedmc.featureframework.toolkit.ToolkitContext;
import nl.hauntedmc.featureframework.toolkit.log.FrameworkLogger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Central registry/factory for YAML files shared by the Paper runtime and feature implementations. */
public final class ConfigService {
    private final Path dataDir;
    private final FrameworkLogger logger;
    private final ClassLoader resources;
    private final ConfigMutationPolicy mutationPolicy;
    private final ConcurrentHashMap<Path, YamlFile> cache = new ConcurrentHashMap<>();

    public ConfigService(ToolkitContext context) {
        this(Objects.requireNonNull(context.getDataDirectory(), "dataDirectory"),
                context.getToolkitLogger() == null ? FrameworkLogger.noop() : context.getToolkitLogger(),
                context.getResourceClassLoader(), ConfigMutationPolicy.allowAll());
    }

    public ConfigService(Path dataDir, FrameworkLogger logger, ClassLoader resources) {
        this(dataDir, logger, resources, ConfigMutationPolicy.allowAll());
    }

    public ConfigService(
            Path dataDir,
            FrameworkLogger logger,
            ClassLoader resources,
            ConfigMutationPolicy mutationPolicy
    ) {
        this.dataDir = Objects.requireNonNull(dataDir, "dataDir").toAbsolutePath().normalize();
        this.logger = Objects.requireNonNull(logger, "logger");
        this.resources = resources == null ? ConfigService.class.getClassLoader() : resources;
        this.mutationPolicy = Objects.requireNonNull(mutationPolicy, "mutationPolicy");
    }

    public ConfigService(Path dataDir, java.util.logging.Logger logger, ClassLoader resources) {
        this(dataDir, FrameworkLogger.from(logger), resources);
    }

    public ConfigService(Path dataDir, org.slf4j.Logger logger, ClassLoader resources) {
        this(dataDir, FrameworkLogger.from(logger), resources);
    }

    public YamlFile open(String relativePath, boolean copyDefaultsIfPresent) {
        Path relative = checkedRelative(relativePath);
        Path absolute = dataDir.resolve(relative).normalize();
        return cache.computeIfAbsent(absolute, path -> {
            try {
                Files.createDirectories(path.getParent());
                if (Files.notExists(path)) {
                    mutationPolicy.checkMutation(relative, copyDefaultsIfPresent ? "create from defaults" : "create");
                    if (copyDefaultsIfPresent) {
                        try (InputStream input = resources.getResourceAsStream(relativePath)) {
                            if (input != null) {
                                Files.copy(input, path);
                                logger.info("[FeatureFramework] Copied default resource '" + relativePath + "'");
                            } else {
                                Files.createFile(path);
                                logger.info("[FeatureFramework] Created empty file '" + relativePath + "'");
                            }
                        }
                    } else {
                        Files.createFile(path);
                        logger.info("[FeatureFramework] Created empty file '" + relativePath + "'");
                    }
                }
                if (!Files.isRegularFile(path)) throw new IllegalStateException("Config path is not a regular file: " + path);
                return new YamlFile(path, relative, logger, mutationPolicy);
            } catch (IOException exception) {
                throw new IllegalStateException("Failed to open YAML file: " + path, exception);
            }
        });
    }

    public Optional<YamlFile> openExisting(String relativePath) {
        Path absolute = resolve(relativePath);
        if (Files.notExists(absolute)) return Optional.empty();
        return Optional.of(open(relativePath, false));
    }

    public boolean exists(String relativePath) { return Files.exists(resolve(relativePath)); }
    public boolean isCached(String relativePath) { return cache.containsKey(resolve(relativePath)); }
    public int cachedFileCount() { return cache.size(); }
    public Set<Path> cachedPaths() { return Set.copyOf(cache.keySet()); }
    public Path dataDirectory() { return dataDir; }
    public ConfigMutationPolicy mutationPolicy() { return mutationPolicy; }

    public void replaceWithEmptyDocument(String relativePath) {
        Path relative = checkedRelative(relativePath);
        Path absolute = dataDir.resolve(relative).normalize();
        YamlFile cached = cache.get(absolute);
        if (cached != null) {
            cached.replaceWithEmptyDocument();
            return;
        }
        mutationPolicy.checkMutation(relative, "replace with empty document");
        Path temporary = null;
        try {
            Files.createDirectories(absolute.getParent());
            temporary = Files.createTempFile(absolute.getParent(), "." + absolute.getFileName(), ".tmp");
            preservePosixPermissions(absolute, temporary);
            try { Files.move(temporary, absolute, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, absolute, StandardCopyOption.REPLACE_EXISTING);
            }
            open(relativePath, false);
        } catch (IOException exception) {
            throw new ConfigPersistenceException(absolute, "replace with empty document", exception);
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

    public void deleteOptional(String relativePath) throws IOException {
        Path relative = checkedRelative(relativePath);
        mutationPolicy.checkMutation(relative, "delete optional file");
        Path absolute = dataDir.resolve(relative).normalize();
        Files.deleteIfExists(absolute);
        cache.remove(absolute);
    }

    public void reloadIfCached(String relativePath) {
        YamlFile cached = cache.get(resolve(relativePath));
        if (cached != null) cached.reload();
    }

    public void evict(String relativePath) { cache.remove(resolve(relativePath)); }

    public Path resolve(String relativePath) { return dataDir.resolve(checkedRelative(relativePath)).normalize(); }

    private Path checkedRelative(String relativePath) {
        Objects.requireNonNull(relativePath, "relativePath");
        if (relativePath.isBlank()) throw new IllegalArgumentException("Config path must not be blank");
        Path relative = Path.of(relativePath).normalize();
        if (relative.isAbsolute()) throw new IllegalArgumentException("Config path must be relative: " + relativePath);
        Path absolute = dataDir.resolve(relative).normalize();
        if (!absolute.startsWith(dataDir)) throw new IllegalArgumentException("Config path escapes data directory: " + relativePath);
        return relative;
    }

    public ConfigView view(String relativePath, boolean copyDefaultsIfPresent) {
        return new ConfigView(open(relativePath, copyDefaultsIfPresent), "");
    }

    public ConfigView view(String relativePath, boolean copyDefaultsIfPresent, String basePath) {
        return new ConfigView(open(relativePath, copyDefaultsIfPresent), basePath);
    }
}
