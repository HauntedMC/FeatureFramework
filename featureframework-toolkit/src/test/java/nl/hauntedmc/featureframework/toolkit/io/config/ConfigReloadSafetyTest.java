package nl.hauntedmc.featureframework.toolkit.io.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConfigReloadSafetyTest {

    @TempDir
    Path tempDir;

    @Test
    void malformedReloadRetainsLastGoodTreeAndBlocksOverwrite() throws Exception {
        Path path = tempDir.resolve("config.yml");
        Files.writeString(path, "value: good\n");

        YamlFile file = new YamlFile(path, Logger.getLogger(ConfigReloadSafetyTest.class.getName()));
        ConfigView view = new ConfigView(file, "");
        assertEquals("good", view.get("value", String.class));

        String malformed = "value: [unterminated\n";
        Files.writeString(path, malformed);

        ConfigLoadException loadFailure = assertThrows(ConfigLoadException.class, file::reload);
        assertEquals(path, loadFailure.path());
        assertEquals("good", view.get("value", String.class));
        assertThrows(ConfigPersistenceException.class, () -> view.put("other", "value"));
        assertEquals(malformed, Files.readString(path));

        Files.writeString(path, "value: fixed\n");
        file.reload();
        view.put("other", "persisted");
        assertEquals("fixed", view.get("value", String.class));
        assertEquals("persisted", view.get("other", String.class));
    }
}
