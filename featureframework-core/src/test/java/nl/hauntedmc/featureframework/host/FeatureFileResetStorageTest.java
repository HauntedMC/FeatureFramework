package nl.hauntedmc.featureframework.host;

import nl.hauntedmc.featureframework.operation.reset.FeatureFileResetRequest;
import nl.hauntedmc.featureframework.operation.reset.MessageResetScope;
import nl.hauntedmc.featureframework.toolkit.io.config.ConfigService;
import nl.hauntedmc.featureframework.toolkit.log.FrameworkLogger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeatureFileResetStorageTest {
    @TempDir
    Path tempDir;

    @Test
    void mainAndOverridesBacksUpStagesAndRestoresExactFiles() throws Exception {
        Path directory = Files.createDirectories(tempDir.resolve("features/Demo"));
        Files.writeString(directory.resolve("messages.yml"), "main: custom\n");
        Files.writeString(directory.resolve("messages_EN.yml"), "main: english\n");
        Files.writeString(directory.resolve("messages_pt-BR.yml"), "main: portuguese\n");
        Files.writeString(directory.resolve("notes.yml"), "keep: true\n");
        FeatureFileResetStorage storage = storage();
        FeatureFileResetRequest request = FeatureFileResetRequest.messages(
                MessageResetScope.MAIN_AND_OVERRIDES);

        FeatureFileResetStorage.Backup backup = storage.begin("Demo", request);
        List<String> deleted = storage.stage(backup, request);

        assertEquals(List.of("messages_EN.yml", "messages_pt-BR.yml"), deleted);
        assertFalse(Files.exists(directory.resolve("messages_EN.yml")));
        assertTrue(Files.exists(directory.resolve("notes.yml")));
        storage.restore(backup);
        storage.markRolledBack(backup);
        assertEquals("main: custom\n", Files.readString(directory.resolve("messages.yml")));
        assertEquals("main: english\n", Files.readString(directory.resolve("messages_EN.yml")));
        assertEquals("main: portuguese\n", Files.readString(directory.resolve("messages_pt-BR.yml")));
    }

    @Test
    void mainOnlyDoesNotCaptureOrDeleteOverrides() throws Exception {
        Path directory = Files.createDirectories(tempDir.resolve("features/Demo"));
        Files.writeString(directory.resolve("messages.yml"), "main: custom\n");
        Files.writeString(directory.resolve("messages_EN.yml"), "main: english\n");
        FeatureFileResetStorage storage = storage();
        FeatureFileResetRequest request = FeatureFileResetRequest.messages(MessageResetScope.MAIN_ONLY);

        FeatureFileResetStorage.Backup backup = storage.begin("Demo", request);
        assertTrue(storage.stage(backup, request).isEmpty());
        assertTrue(Files.exists(directory.resolve("messages_EN.yml")));
    }

    @Test
    void startupRecoveryRestoresPreparedJournal() throws Exception {
        Path config = tempDir.resolve("features/Demo/config.yml");
        Files.createDirectories(config.getParent());
        Files.writeString(config, "enabled: true\ncustom: value\n");
        FeatureFileResetStorage storage = storage();
        FeatureFileResetRequest request = FeatureFileResetRequest.config();
        FeatureFileResetStorage.Backup backup = storage.begin("Demo", request);
        storage.stage(backup, request);
        assertFalse(Files.readString(config).contains("custom"));

        storage().recoverIncompleteTransactions();

        assertEquals("enabled: true\ncustom: value\n", Files.readString(config));
    }

    @Test
    void rejectsSymlinkedFeatureDirectory() throws Exception {
        Path external = Files.createDirectories(tempDir.resolve("external"));
        Path features = Files.createDirectories(tempDir.resolve("features"));
        try {
            Files.createSymbolicLink(features.resolve("Demo"), external);
        } catch (UnsupportedOperationException exception) {
            return;
        }
        assertThrows(FeatureFileResetStorage.UnsafeTargetException.class,
                () -> storage().targets("Demo", FeatureFileResetRequest.config()));
    }

    @Test
    void malformedNonTargetIsTemporarilyStagedAndRestored() throws Exception {
        Path directory = Files.createDirectories(tempDir.resolve("features/Demo"));
        Files.writeString(directory.resolve("config.yml"), "enabled: [broken");
        Files.writeString(directory.resolve("messages.yml"), "message: [also-broken");
        FeatureFileResetStorage storage = storage();
        FeatureFileResetStorage.Backup backup = storage.begin("Demo", FeatureFileResetRequest.config());

        storage.stage(backup, FeatureFileResetRequest.config());
        assertTrue(storage.stageMalformedPrerequisites(backup));
        storage.restorePrerequisites(backup);

        assertEquals("message: [also-broken", Files.readString(directory.resolve("messages.yml")));
    }

    private FeatureFileResetStorage storage() {
        ConfigService service = new ConfigService(tempDir, FrameworkLogger.noop(), getClass().getClassLoader());
        return new FeatureFileResetStorage(service, FrameworkLogger.noop());
    }
}
