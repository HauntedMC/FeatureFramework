package nl.hauntedmc.featureframework.toolkit.io.cache.impl;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.Strictness;
import com.google.gson.reflect.TypeToken;
import nl.hauntedmc.featureframework.toolkit.io.cache.CacheValue;
import nl.hauntedmc.featureframework.toolkit.io.cache.FileCacheStore;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Atomic, corruption-tolerant JSON cache store.
 *
 * <p>Instances that point at the same normalized path share a lock and reload before each
 * operation. This prevents independent feature/runtime instances from overwriting one another
 * during reloads or concurrent writes.</p>
 */
public class JsonCacheFile implements FileCacheStore {
    private static final Logger LOGGER = Logger.getLogger(JsonCacheFile.class.getName());
    private static final String EXPIRATION = "expirationTimestamp";
    private static final String VALUE = "value";
    private static final int FILE_LOCK_STRIPES = 256;
    private static final Type RAW_MAP_TYPE = new TypeToken<Map<String, Map<String, Object>>>() { }.getType();
    private static final ReentrantLock[] FILE_LOCKS = createFileLocks();

    private final File file;
    private final Path path;
    private final Gson gson = new GsonBuilder().setStrictness(Strictness.STRICT).create();
    private final ReentrantLock fileLock;
    private Map<String, Map<String, Object>> rawMap = new LinkedHashMap<>();

    public JsonCacheFile(File file) {
        this.path = Objects.requireNonNull(file, "file").toPath().toAbsolutePath().normalize();
        this.file = path.toFile();
        this.fileLock = FILE_LOCKS[Math.floorMod(path.hashCode(), FILE_LOCKS.length)];

        fileLock.lock();
        try {
            ensureFileExistsLocked();
            loadLocked();
        } finally {
            fileLock.unlock();
        }
    }

    @Override
    public File getUnderlyingFile() {
        return file;
    }

    @Override
    public void put(String key, CacheValue value) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");

        fileLock.lock();
        try {
            loadLocked();
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put(VALUE, value.getData());
            entry.put(EXPIRATION, value.getExpirationTimestamp());
            rawMap.put(key, entry);
            saveLocked();
        } finally {
            fileLock.unlock();
        }
    }

    @Override
    public void cleanupExpired() {
        fileLock.lock();
        try {
            loadLocked();
            cleanupExpiredLocked();
        } finally {
            fileLock.unlock();
        }
    }

    @Override
    public CacheValue get(String key) {
        Objects.requireNonNull(key, "key");

        fileLock.lock();
        try {
            loadLocked();
            cleanupExpiredLocked();
            return toValue(rawMap.get(key));
        } finally {
            fileLock.unlock();
        }
    }

    @Override
    public void remove(String key) {
        Objects.requireNonNull(key, "key");

        fileLock.lock();
        try {
            loadLocked();
            if (rawMap.remove(key) == null) {
                return;
            }
            if (rawMap.isEmpty()) {
                deleteLocked();
            } else {
                saveLocked();
            }
        } finally {
            fileLock.unlock();
        }
    }

    @Override
    public Map<String, CacheValue> listAll() {
        fileLock.lock();
        try {
            loadLocked();
            cleanupExpiredLocked();
            Map<String, CacheValue> output = new LinkedHashMap<>();
            rawMap.forEach((key, entry) -> {
                CacheValue value = toValue(entry);
                if (value != null) {
                    output.put(key, value);
                }
            });
            return output;
        } finally {
            fileLock.unlock();
        }
    }

    /** Returns a stable snapshot of all stored keys. */
    public Set<String> getKeys() {
        fileLock.lock();
        try {
            loadLocked();
            cleanupExpiredLocked();
            return new LinkedHashSet<>(rawMap.keySet());
        } finally {
            fileLock.unlock();
        }
    }

    @Override
    public Map<String, CacheValue> find(String regex) {
        Objects.requireNonNull(regex, "regex");
        Pattern pattern = Pattern.compile(regex);

        fileLock.lock();
        try {
            loadLocked();
            cleanupExpiredLocked();
            Map<String, CacheValue> output = new LinkedHashMap<>();
            rawMap.forEach((key, entry) -> {
                if (pattern.matcher(key).matches()) {
                    CacheValue value = toValue(entry);
                    if (value != null) {
                        output.put(key, value);
                    }
                }
            });
            return output;
        } finally {
            fileLock.unlock();
        }
    }

    @Override
    public boolean isEmpty() {
        fileLock.lock();
        try {
            loadLocked();
            cleanupExpiredLocked();
            return rawMap.isEmpty();
        } finally {
            fileLock.unlock();
        }
    }

    @Override
    public void delete() {
        fileLock.lock();
        try {
            deleteLocked();
        } finally {
            fileLock.unlock();
        }
    }

    private static ReentrantLock[] createFileLocks() {
        ReentrantLock[] locks = new ReentrantLock[FILE_LOCK_STRIPES];
        for (int index = 0; index < locks.length; index++) {
            locks[index] = new ReentrantLock();
        }
        return locks;
    }

    private void ensureFileExistsLocked() {
        try {
            Files.createDirectories(path.getParent());
            if (Files.notExists(path)) {
                Files.createFile(path);
            } else if (!Files.isRegularFile(path)) {
                throw new IllegalStateException("Cache path is not a regular file: " + file);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot create cache file " + file, exception);
        }
    }

    private void loadLocked() {
        if (Files.notExists(path)) {
            rawMap = new LinkedHashMap<>();
            return;
        }

        try {
            String json = Files.readString(path, StandardCharsets.UTF_8);
            if (json.isBlank()) {
                rawMap = new LinkedHashMap<>();
                return;
            }
            rawMap = parseMap(json);
        } catch (JsonParseException exception) {
            recoverMalformedFileLocked(exception);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot load cache file " + file, exception);
        }
    }

    private Map<String, Map<String, Object>> parseMap(String json) {
        Map<String, Map<String, Object>> parsed = gson.fromJson(json, RAW_MAP_TYPE);
        return parsed == null ? new LinkedHashMap<>() : new LinkedHashMap<>(parsed);
    }

    private void recoverMalformedFileLocked(JsonParseException parseFailure) {
        final String malformedJson;
        final Path backup;
        try {
            malformedJson = Files.readString(path, StandardCharsets.UTF_8);
            backup = createCorruptionBackupLocked();
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot preserve malformed cache file " + file, exception);
        }

        String recoverablePrefix = firstCompleteJsonObject(malformedJson);
        if (recoverablePrefix != null) {
            try {
                rawMap = parseMap(recoverablePrefix);
                saveLocked();
                LOGGER.warning("Recovered malformed JSON cache " + path
                        + " from its first complete object; original preserved at " + backup
                        + ". Parse failure: " + parseFailure.getMessage());
                return;
            } catch (JsonParseException ignored) {
                // The first syntactically complete object was not a valid cache document.
            }
        }

        rawMap = new LinkedHashMap<>();
        saveLocked();
        LOGGER.severe("Quarantined unreadable JSON cache " + path
                + " and restarted it empty; original preserved at " + backup
                + ". Parse failure: " + parseFailure.getMessage());
    }

    private Path createCorruptionBackupLocked() throws IOException {
        long timestamp = System.currentTimeMillis();
        Path backup = path.resolveSibling(path.getFileName() + ".corrupt-" + timestamp + ".bak");
        int suffix = 0;
        while (Files.exists(backup)) {
            suffix++;
            backup = path.resolveSibling(path.getFileName()
                    + ".corrupt-" + timestamp + '-' + suffix + ".bak");
        }
        return Files.copy(path, backup);
    }

    private static String firstCompleteJsonObject(String json) {
        int start = 0;
        while (start < json.length()
                && (Character.isWhitespace(json.charAt(start)) || json.charAt(start) == '\uFEFF')) {
            start++;
        }
        if (start >= json.length() || json.charAt(start) != '{') {
            return null;
        }

        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int index = start; index < json.length(); index++) {
            char current = json.charAt(index);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if (current == '"') {
                    inString = false;
                }
                continue;
            }

            if (current == '"') {
                inString = true;
            } else if (current == '{') {
                depth++;
            } else if (current == '}') {
                depth--;
                if (depth == 0) {
                    return json.substring(start, index + 1);
                }
                if (depth < 0) {
                    return null;
                }
            }
        }
        return null;
    }

    private void saveLocked() {
        Path temporary = null;
        try {
            Files.createDirectories(path.getParent());
            temporary = Files.createTempFile(path.getParent(), temporaryFilePrefix(), ".tmp");
            try (FileOutputStream output = new FileOutputStream(temporary.toFile());
                 Writer writer = new OutputStreamWriter(output, StandardCharsets.UTF_8)) {
                gson.toJson(rawMap, writer);
                writer.flush();
                output.getFD().sync();
            }

            try {
                Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot save cache file " + file, exception);
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) {
                    temporary.toFile().deleteOnExit();
                }
            }
        }
    }

    private String temporaryFilePrefix() {
        String prefix = path.getFileName().toString() + '.';
        return prefix.length() >= 3 ? prefix : (prefix + "___").substring(0, 3);
    }

    private void cleanupExpiredLocked() {
        long now = System.currentTimeMillis();
        boolean changed = rawMap.entrySet().removeIf(entry -> isExpired(entry.getValue(), now));
        if (!changed) {
            return;
        }
        if (rawMap.isEmpty()) {
            deleteLocked();
        } else {
            saveLocked();
        }
    }

    private static boolean isExpired(Map<String, Object> entry, long now) {
        if (entry == null) {
            return false;
        }
        Object expiration = entry.get(EXPIRATION);
        if (!(expiration instanceof Number number)) {
            return false;
        }
        long timestamp = number.longValue();
        return timestamp > 0 && now > timestamp;
    }

    private static CacheValue toValue(Map<String, Object> entry) {
        if (entry == null || !(entry.get(VALUE) instanceof Map<?, ?> rawData)) {
            return null;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        rawData.forEach((key, value) -> {
            if (key instanceof String stringKey) {
                data.put(stringKey, value);
            }
        });
        Object expiration = entry.get(EXPIRATION);
        long timestamp = expiration instanceof Number number ? number.longValue() : -1L;
        return CacheValue.of(data, timestamp);
    }

    private void deleteLocked() {
        try {
            Files.deleteIfExists(path);
            rawMap.clear();
        } catch (IOException exception) {
            file.deleteOnExit();
            throw new IllegalStateException("Cannot delete cache file " + file, exception);
        }
    }
}
