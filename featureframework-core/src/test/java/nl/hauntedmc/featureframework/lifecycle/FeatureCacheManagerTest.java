package nl.hauntedmc.featureframework.lifecycle;

import nl.hauntedmc.featureframework.toolkit.log.FrameworkLogger;
import nl.hauntedmc.featureframework.toolkit.ToolkitContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FeatureCacheManagerTest {

    @Test
    void createsScopedDirectoriesAndEnforcesLifecycle(@TempDir Path dataDirectory) {
        FeatureCacheManager manager = new FeatureCacheManager(dataDirectory, FrameworkLogger.noop());

        var directory = manager.getCacheDirectory("Demo", "items");
        assertTrue(directory.getDirectory().toPath().startsWith(dataDirectory.resolve("cache")));

        manager.cleanupAll();
        assertEquals(FeatureResourceState.CLOSED, manager.state());
        assertThrows(IllegalStateException.class, () -> manager.getCacheDirectory("Demo", "other"));
    }

    @Test
    void rejectsAFileAtTheCachePath(@TempDir Path dataDirectory) throws IOException {
        Files.writeString(dataDirectory.resolve("cache"), "not a directory");

        assertThrows(
                IllegalStateException.class,
                () -> new FeatureCacheManager(dataDirectory, FrameworkLogger.noop())
        );
    }

    @Test
    void acceptsTheSharedToolkitHostContract(@TempDir Path dataDirectory) {
        ToolkitContext context = mock(ToolkitContext.class);
        when(context.getDataDirectory()).thenReturn(dataDirectory);
        when(context.getToolkitLogger()).thenReturn(FrameworkLogger.noop());

        FeatureCacheManager manager = new FeatureCacheManager(context);

        assertTrue(manager.getCacheDirectory("Demo", "default").getDirectory().isDirectory());
    }
}
