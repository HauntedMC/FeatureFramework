package nl.hauntedmc.featureframework.toolkit.io.cache;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.Strictness;
import nl.hauntedmc.featureframework.toolkit.io.cache.impl.JsonCacheFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class JsonCacheFileTest {

    @TempDir
    Path tempDir;

    @Test
    void valuesPersistReloadAndRegexLookupWorks() {
        Path file = tempDir.resolve("cache.json");
        JsonCacheFile cache = new JsonCacheFile(file.toFile());
        cache.put("player:one", CacheValue.of(Map.of("value", 1), System.currentTimeMillis() + 60_000));
        cache.put("other", CacheValue.of(Map.of("value", 2), System.currentTimeMillis() + 60_000));

        Number storedValue = (Number) cache.get("player:one").getData().get("value");
        assertEquals(1, storedValue.intValue());
        assertEquals(2, cache.listAll().size());
        assertEquals(1, cache.find("player:.*").size());

        JsonCacheFile reloaded = new JsonCacheFile(file.toFile());
        assertEquals(2, reloaded.listAll().size());
        assertFalse(reloaded.isEmpty());
    }

    @Test
    void nonPositiveExpirationRemainsNonExpiringAfterPersistence() {
        Path file = tempDir.resolve("never-expires.json");
        JsonCacheFile cache = new JsonCacheFile(file.toFile());
        cache.put("zero", CacheValue.of(Map.of("value", 1), 0));
        cache.put("negative", CacheValue.of(Map.of("value", 2), -1));

        JsonCacheFile reloaded = new JsonCacheFile(file.toFile());

        assertNotNull(reloaded.get("zero"));
        assertNotNull(reloaded.get("negative"));
        assertEquals(2, reloaded.listAll().size());
        assertTrue(Files.exists(file));
    }

    @Test
    void expiredEntriesAreRemovedAndEmptyStoreDeletesFile() {
        Path file = tempDir.resolve("expired.json");
        JsonCacheFile cache = new JsonCacheFile(file.toFile());
        cache.put("expired", CacheValue.of(Map.of("x", 1), System.currentTimeMillis() - 1));

        assertNull(cache.get("expired"));
        assertTrue(cache.isEmpty());
        assertFalse(Files.exists(file));
    }

    @Test
    void removePersistsAndDeletesAnEmptyStore() {
        Path file = tempDir.resolve("remove.json");
        JsonCacheFile cache = new JsonCacheFile(file.toFile());
        cache.put("first", CacheValue.of(Map.of("x", 1), System.currentTimeMillis() + 60_000));
        cache.put("second", CacheValue.of(Map.of("x", 2), System.currentTimeMillis() + 60_000));

        cache.remove("first");
        assertNull(new JsonCacheFile(file.toFile()).get("first"));
        assertFalse(cache.isEmpty());

        cache.remove("second");
        assertTrue(cache.isEmpty());
        assertFalse(Files.exists(file));
    }

    @Test
    void deleteClearsStoreAndInvalidParentFailsCreation() throws Exception {
        Path file = tempDir.resolve("delete.json");
        JsonCacheFile cache = new JsonCacheFile(file.toFile());
        cache.put("x", CacheValue.of(Map.of("x", 1), System.currentTimeMillis() + 60_000));
        cache.delete();
        assertTrue(cache.isEmpty());

        Path blocker = tempDir.resolve("blocker");
        Files.writeString(blocker, "x");
        assertThrows(IllegalStateException.class,
                () -> new JsonCacheFile(blocker.resolve("cache.json").toFile()));
    }

    @Test
    void concurrentInstancesPreserveAllEntries() throws Exception {
        File file = tempDir.resolve("concurrent/votes.json").toFile();
        int voteCount = 80;
        ExecutorService executor = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> writes = new ArrayList<>();

        try {
            for (int index = 0; index < voteCount; index++) {
                int voteIndex = index;
                writes.add(executor.submit(() -> {
                    start.await();
                    JsonCacheFile store = new JsonCacheFile(file);
                    store.put("vote-" + voteIndex, CacheValue.of(
                            Map.of("service", "service-" + voteIndex),
                            System.currentTimeMillis() + 60_000));
                    return null;
                }));
            }

            start.countDown();
            for (Future<?> write : writes) {
                write.get(15, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(15, TimeUnit.SECONDS));
        }

        JsonCacheFile reloaded = new JsonCacheFile(file);
        assertEquals(voteCount, reloaded.listAll().size());
        for (int index = 0; index < voteCount; index++) {
            assertEquals("service-" + index,
                    reloaded.get("vote-" + index).getData().get("service"));
        }
        assertStrictJson(file.toPath());
    }

    @Test
    void repairsTrailingGarbageAndPreservesOriginal() throws Exception {
        Path file = tempDir.resolve("recovery/votes.json");
        Files.createDirectories(file.getParent());
        long expiration = System.currentTimeMillis() + 60_000;
        String key = "vote.b5cfd842-5455-38a2-9c0d-da059d1e39e5";
        Files.writeString(file, "{\"" + key + "\":{\"value\":{\"service\":\"MinecraftKrant\"},"
                + "\"expirationTimestamp\":" + expiration + "}}242}}", StandardCharsets.UTF_8);

        JsonCacheFile recovered = new JsonCacheFile(file.toFile());

        assertEquals("MinecraftKrant", recovered.get(key).getData().get("service"));
        String repairedJson = Files.readString(file, StandardCharsets.UTF_8);
        assertFalse(repairedJson.endsWith("242}}"));
        assertStrictJson(file);
        assertTrue(hasCorruptionBackup(file));
    }

    @Test
    void quarantinesUnrecoverableDocumentAndRemainsWritable() throws Exception {
        Path file = tempDir.resolve("quarantine/votes.json");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "{\"vote\":", StandardCharsets.UTF_8);

        JsonCacheFile recovered = new JsonCacheFile(file.toFile());

        assertTrue(recovered.isEmpty());
        recovered.put("replacement", CacheValue.of(
                Map.of("service", "MinecraftKrant"), System.currentTimeMillis() + 60_000));
        assertEquals("MinecraftKrant", recovered.get("replacement").getData().get("service"));
        assertStrictJson(file);
        assertTrue(hasCorruptionBackup(file));
    }

    private static void assertStrictJson(Path file) throws Exception {
        String json = Files.readString(file, StandardCharsets.UTF_8);
        JsonElement parsed = assertDoesNotThrow(() -> new GsonBuilder()
                .setStrictness(Strictness.STRICT)
                .create()
                .fromJson(json, JsonElement.class));
        assertTrue(parsed.isJsonObject());
    }

    private static boolean hasCorruptionBackup(Path file) throws Exception {
        String prefix = file.getFileName() + ".corrupt-";
        try (Stream<Path> files = Files.list(file.getParent())) {
            return files.anyMatch(candidate -> candidate.getFileName().toString().startsWith(prefix));
        }
    }
}
