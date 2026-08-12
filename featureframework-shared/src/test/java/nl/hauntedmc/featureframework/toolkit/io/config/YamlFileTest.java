package nl.hauntedmc.featureframework.toolkit.io.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class YamlFileTest {

    @TempDir
    Path tempDir;

    @Test
    void readWriteMutateAndRootReplacementWork() throws Exception {
        Path path = tempDir.resolve("config.yml");
        Files.createFile(path);
        YamlFile yaml = new YamlFile(path, mock(Logger.class));

        yaml.setRawAndSave("global.name", "server");
        assertEquals("server", yaml.getRaw("global.name"));
        assertNull(yaml.getRaw("global.missing"));

        yaml.mutateAndSave(root -> root.node("global", "enabled").raw(true));
        assertEquals(true, yaml.getRaw("global.enabled"));

        yaml.setRawAndSave("", Map.of("x", 1));
        assertEquals(1, ((Map<?, ?>) yaml.getRaw("")).get("x"));
        assertArrayEquals(new Object[]{"a", "b", "c"}, YamlFile.splitPath("a.b.c"));
        assertArrayEquals(new Object[0], YamlFile.splitPath(""));
    }

    @Test
    void malformedYamlFailsWithoutInventingReplacementState() throws Exception {
        Path path = tempDir.resolve("broken.yml");
        Files.writeString(path, "global: [broken");

        ConfigLoadException failure = assertThrows(ConfigLoadException.class,
                () -> new YamlFile(path, mock(Logger.class)));
        assertEquals(path, failure.path());
        assertTrue(Files.readString(path).contains("[broken"));
    }

    @Test
    void failedPersistenceDoesNotPublishCandidateInMemory() throws Exception {
        Path path = tempDir.resolve("stable.yml");
        Files.createFile(path);
        YamlFile yaml = new YamlFile(path, mock(Logger.class));
        yaml.setRawAndSave("value", 1);

        Files.delete(path);
        Files.createDirectory(path);
        assertThrows(ConfigPersistenceException.class, () -> yaml.setRawAndSave("value", 2));
        assertEquals(1, yaml.getRaw("value"));
    }
}
