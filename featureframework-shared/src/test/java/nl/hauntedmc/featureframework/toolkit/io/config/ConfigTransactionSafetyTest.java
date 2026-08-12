package nl.hauntedmc.featureframework.toolkit.io.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class ConfigTransactionSafetyTest {

    @TempDir
    Path tempDir;

    @Test
    void failedBatchLeavesMemoryAndDiskUnchanged() throws Exception {
        Path path = tempDir.resolve("batch.yml");
        Files.createFile(path);
        ConfigView view = view(path);
        view.put("value", "before");
        String persistedBefore = Files.readString(path);

        assertThrows(RuntimeException.class, () -> view.batch(batch -> {
            try {
                batch.put("value", "after");
                batch.put("other", true);
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            }
            throw new RuntimeException("abort");
        }));

        assertEquals("before", view.get("value", String.class));
        assertFalse(view.node("other").isPresent());
        assertEquals(persistedBefore, Files.readString(path));
    }

    @Test
    void concurrentPutIfAbsentHasExactlyOneWinner() throws Exception {
        Path path = tempDir.resolve("concurrent.yml");
        Files.createFile(path);
        ConfigView view = view(path);
        int contenders = 16;
        ExecutorService executor = Executors.newFixedThreadPool(contenders);
        CountDownLatch ready = new CountDownLatch(contenders);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger winners = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();

        try {
            for (int index = 0; index < contenders; index++) {
                int value = index;
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    assertTrue(start.await(5, TimeUnit.SECONDS));
                    if (view.putIfAbsent("winner", value)) {
                        winners.incrementAndGet();
                    }
                    return null;
                }));
            }
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            for (Future<?> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        assertEquals(1, winners.get());
        assertTrue(view.node("winner").isPresent());
        try (var files = Files.list(tempDir)) {
            assertFalse(files.anyMatch(candidate -> candidate.getFileName().toString().endsWith(".tmp")));
        }
    }

    private static ConfigView view(Path path) {
        return new ConfigView(
                new YamlFile(path, Logger.getLogger(ConfigTransactionSafetyTest.class.getName())),
                ""
        );
    }
}
