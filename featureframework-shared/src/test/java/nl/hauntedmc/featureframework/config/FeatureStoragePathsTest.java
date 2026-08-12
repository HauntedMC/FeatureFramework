package nl.hauntedmc.featureframework.config;

import nl.hauntedmc.featureframework.toolkit.io.localization.Language;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FeatureStoragePathsTest {

    @Test
    void buildsFeatureOwnedConfigAndMessagePaths() {
        assertEquals("features/Demo/config.yml", FeatureStoragePaths.configPath(" Demo "));
        assertEquals("features/Demo/messages.yml", FeatureStoragePaths.messagesPath("Demo"));
        assertEquals(
                "features/Demo/" + Language.EN.getFileName(),
                FeatureStoragePaths.messagesPath("Demo", Language.EN)
        );
    }

    @Test
    void buildsSafeLocalDataPaths() {
        assertEquals(
                "local/commandscheduler.yml",
                FeatureStoragePaths.localDataPath(" commandscheduler.yml ")
        );
        assertEquals("local/recipes.yaml", FeatureStoragePaths.localDataPath("recipes.yaml"));
    }

    @Test
    void rejectsUnsafeFeatureNames() {
        assertThrows(IllegalArgumentException.class, () -> FeatureStoragePaths.configPath("../Demo"));
        assertThrows(IllegalArgumentException.class, () -> FeatureStoragePaths.configPath("Demo/Child"));
        assertThrows(IllegalArgumentException.class, () -> FeatureStoragePaths.configPath(" "));
    }

    @Test
    void rejectsUnsafeLocalDataFileNames() {
        assertThrows(
                IllegalArgumentException.class,
                () -> FeatureStoragePaths.localDataPath("../commandscheduler.yml")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> FeatureStoragePaths.localDataPath("nested/commandscheduler.yml")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> FeatureStoragePaths.localDataPath("commandscheduler.txt")
        );
    }
}
