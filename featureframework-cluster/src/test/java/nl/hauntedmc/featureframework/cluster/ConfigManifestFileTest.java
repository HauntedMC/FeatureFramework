package nl.hauntedmc.featureframework.cluster;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConfigManifestFileTest {
    private static final String SHA256 = "0".repeat(64);

    @Test
    void acceptsStorageMaximumAndRejectsLongerManagedPaths() {
        String maximum = "a".repeat(ConfigManifestFile.MAX_PATH_LENGTH);
        assertEquals(maximum, new ConfigManifestFile(maximum, "ROOT_CONFIG", SHA256, 0).path());

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> new ConfigManifestFile(maximum + "b", "ROOT_CONFIG", SHA256, 0));
        assertEquals("path must be at most 255 characters", failure.getMessage());
    }
}
