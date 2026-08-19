package nl.hauntedmc.featureframework.toolkit.io.config;

import nl.hauntedmc.featureframework.toolkit.ToolkitContext;
import nl.hauntedmc.featureframework.toolkit.log.FrameworkLogger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConfigServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void openCachesFilesAndRejectsPathEscape() {
        ConfigService service = new ConfigService(tempDir, mock(Logger.class), getClass().getClassLoader());
        YamlFile first = service.open("config.yml", false);
        YamlFile second = service.open("config.yml", false);

        assertSame(first, second);
        assertTrue(service.exists("config.yml"));
        assertTrue(service.isCached("config.yml"));
        assertEquals(1, service.cachedFileCount());
        assertEquals(java.util.Set.of(tempDir.resolve("config.yml").toAbsolutePath().normalize()), service.cachedPaths());
        assertThrows(UnsupportedOperationException.class,
                () -> service.cachedPaths().add(tempDir.resolve("other.yml")));
        assertThrows(IllegalArgumentException.class, () -> service.open("../evil.yml", false));
        assertThrows(IllegalArgumentException.class, () -> service.open(" ", false));
        assertThrows(IllegalArgumentException.class,
                () -> service.open(tempDir.resolve("absolute.yml").toString(), false));
    }

    @Test
    void openCopiesDefaultsOrCreatesEmptyFile() throws Exception {
        ClassLoader loader = new ClassLoader(getClass().getClassLoader()) {
            @Override
            public InputStream getResourceAsStream(String name) {
                if ("defaults.yml".equals(name)) {
                    return new ByteArrayInputStream("value: 7\n".getBytes(StandardCharsets.UTF_8));
                }
                return null;
            }
        };
        ConfigService service = new ConfigService(tempDir, mock(Logger.class), loader);

        service.open("defaults.yml", true);
        assertTrue(Files.readString(tempDir.resolve("defaults.yml")).contains("value: 7"));
        service.open("empty.yml", true);
        assertTrue(Files.exists(tempDir.resolve("empty.yml")));
        assertTrue(service.openExisting("missing.yml").isEmpty());
        assertTrue(service.openExisting("defaults.yml").isPresent());
    }

    @Test
    void contextConstructorAndViewHelpersWork() {
        ToolkitContext context = mock(ToolkitContext.class);
        when(context.getDataDirectory()).thenReturn(tempDir);
        when(context.getToolkitLogger()).thenReturn(mock(FrameworkLogger.class));
        when(context.getResourceClassLoader()).thenReturn(getClass().getClassLoader());

        ConfigService service = new ConfigService(context);
        ConfigView root = service.view("root.yml", false);
        ConfigView scoped = service.view("scoped.yml", false, "global");
        root.put("value", 3);
        scoped.put("name", "server");

        assertEquals(3, root.get("value", Integer.class));
        assertEquals("server", scoped.get("name", String.class));
        assertNotNull(service.resolve("root.yml"));
    }

    @Test
    void openWrapsFilesystemFailure() throws Exception {
        Path blocked = tempDir.resolve("blocked");
        Files.writeString(blocked, "file");
        ConfigService service = new ConfigService(blocked, mock(Logger.class), null);

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> service.open("nested.yml", false));
        assertTrue(failure.getMessage().contains("Failed to open YAML file"));
    }

    @Test
    void rejectsDirectoriesAsYamlFiles() throws Exception {
        Files.createDirectories(tempDir.resolve("config.yml"));
        ConfigService service = new ConfigService(tempDir, mock(Logger.class), null);

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> service.open("config.yml", false));
        assertTrue(failure.getMessage().contains("regular file"));
    }

    @Test
    void explicitReplacementRecoversInvalidYamlThatCouldNotEnterTheCache() throws Exception {
        Path path = tempDir.resolve("broken.yml");
        Files.writeString(path, "key: [unterminated");
        ConfigService service = new ConfigService(tempDir, mock(Logger.class), null);

        assertThrows(ConfigLoadException.class, () -> service.open("broken.yml", false));
        service.replaceWithEmptyDocument("broken.yml");

        YamlFile recovered = service.open("broken.yml", false);
        new ConfigView(recovered, "").put("healthy", true);
        assertTrue(Files.readString(path).contains("healthy: true"));
    }

    @Test
    void explicitReplacementRecoversAHandleWhoseLatestReloadFailed() throws Exception {
        Path path = tempDir.resolve("cached-broken.yml");
        ConfigService service = new ConfigService(tempDir, mock(Logger.class), null);
        YamlFile cached = service.open("cached-broken.yml", false);
        Files.writeString(path, "key: [unterminated");
        assertThrows(ConfigLoadException.class, cached::reload);
        assertTrue(cached.hasLoadFailure());
        assertTrue(cached.loadFailure().isPresent());

        service.replaceWithEmptyDocument("cached-broken.yml");

        assertSame(cached, service.open("cached-broken.yml", false));
        assertFalse(cached.hasLoadFailure());
        new ConfigView(cached, "").put("healthy", true);
        assertTrue(Files.readString(path).contains("healthy: true"));
    }

    @Test
    void deletingOptionalFileEvictsItsCachedHandle() throws Exception {
        ConfigService service = new ConfigService(tempDir, mock(Logger.class), null);
        YamlFile original = service.open("optional.yml", false);
        assertTrue(service.isCached("optional.yml"));
        service.deleteOptional("optional.yml");
        assertFalse(service.exists("optional.yml"));
        assertFalse(service.isCached("optional.yml"));

        YamlFile recreated = service.open("optional.yml", false);
        assertNotSame(original, recreated);
    }
}
