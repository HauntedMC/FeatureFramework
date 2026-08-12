package nl.hauntedmc.featureframework.toolkit.io.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.Set;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ConfigPersistenceFailureTest {

    @TempDir
    Path tempDir;

    @Test
    void writeFailuresReachCallersInsteadOfBeingLoggedAndSwallowed() throws Exception {
        Path directory = tempDir.resolve("readonly");
        Files.createDirectories(directory);
        Path path = directory.resolve("config.yml");
        Files.createFile(path);
        assumeTrue(Files.getFileAttributeView(directory, PosixFileAttributeView.class) != null);

        ConfigView view = new ConfigView(
                new YamlFile(path, Logger.getLogger(ConfigPersistenceFailureTest.class.getName())),
                ""
        );
        view.appendToList("items", "one");

        Set<PosixFilePermission> original = Files.getPosixFilePermissions(directory);
        Files.setPosixFilePermissions(directory, EnumSet.of(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_EXECUTE
        ));
        try {
            ConfigPersistenceException putFailure = assertThrows(
                    ConfigPersistenceException.class,
                    () -> view.put("value", "unpersisted")
            );
            assertEquals(path, putFailure.path());
            assertThrows(
                    ConfigPersistenceException.class,
                    () -> view.removeFromList("items", ignored -> true)
            );
            assertEquals("one", view.getList("items", String.class).getFirst());
        } finally {
            Files.setPosixFilePermissions(directory, original);
        }
    }
}
