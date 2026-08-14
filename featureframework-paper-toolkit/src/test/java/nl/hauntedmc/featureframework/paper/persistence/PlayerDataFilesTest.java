package nl.hauntedmc.featureframework.paper.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlayerDataFilesTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void resolvesPaperPlayerDataDirectoryAndFile() {
        Path level = temporaryDirectory.resolve("world");
        UUID playerId = UUID.randomUUID();

        assertEquals(
                level.toAbsolutePath().normalize().resolve("players/data"),
                PlayerDataFiles.dataDirectory(level)
        );
        assertEquals(
                level.toAbsolutePath().normalize().resolve("players/data").resolve(playerId + ".dat"),
                PlayerDataFiles.playerFile(level, playerId)
        );
    }
}
