package nl.hauntedmc.featureframework.paper.persistence;

import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;

/** Safely resolves feature-owned player data beneath a Paper level directory. */
public final class PlayerDataFiles {
    private static final Path PLAYER_DATA_RELATIVE_PATH = Path.of("players", "data");

    private PlayerDataFiles() {
    }

    public static Path dataDirectory(Path levelDirectory) {
        Path level = Objects.requireNonNull(levelDirectory, "levelDirectory").toAbsolutePath().normalize();
        Path directory = level.resolve(PLAYER_DATA_RELATIVE_PATH).normalize();
        if (!directory.startsWith(level)) {
            throw new IllegalArgumentException("Player data directory escaped the level directory");
        }
        return directory;
    }

    public static Path playerFile(Path levelDirectory, UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        Path directory = dataDirectory(levelDirectory);
        Path file = directory.resolve(playerId + ".dat").normalize();
        if (!file.getParent().equals(directory)) {
            throw new IllegalArgumentException("Player data file escaped the player data directory");
        }
        return file;
    }
}
