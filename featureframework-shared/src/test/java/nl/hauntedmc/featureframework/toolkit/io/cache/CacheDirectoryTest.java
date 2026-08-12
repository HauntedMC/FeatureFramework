package nl.hauntedmc.featureframework.toolkit.io.cache;

import nl.hauntedmc.featureframework.toolkit.io.cache.impl.JsonCacheFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.Serial;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class CacheDirectoryTest {

    @TempDir
    Path tempDir;

    @Test
    void createsSanitizedDirectoryAndJsonStore() {
        CacheDirectory directory = new CacheDirectory(tempDir.toFile(), "../feature", "  cache/id ");

        assertTrue(directory.getDirectory().isDirectory());
        assertTrue(directory.getDirectory().getName().contains("feature"));
        assertTrue(directory.getDirectory().getName().contains("cache"));
        assertInstanceOf(JsonCacheFile.class, directory.getStore("../players", CacheType.JSON));
    }

    @Test
    void defaultsAreUsedForNullBlankAndInvalidSegments() {
        CacheDirectory directory = new CacheDirectory(tempDir.toFile(), null, "   ");
        assertTrue(directory.getDirectory().getName().contains("feature-cache"));
        assertTrue(directory.getStore("", CacheType.JSON).getUnderlyingFile().getName().startsWith("store"));

        CacheDirectory invalid = new CacheDirectory(tempDir.toFile(), "\u0000", "cache");
        assertTrue(invalid.getDirectory().getName().startsWith("feature-"));
    }

    @Test
    void constructorFailsWhenDirectoryCannotBeCreated() throws IOException {
        Path baseFile = tempDir.resolve("base-file");
        Files.writeString(baseFile, "x");

        assertThrows(IllegalStateException.class,
                () -> new CacheDirectory(baseFile.toFile(), "feature", "cache"));
    }

    @Test
    void constructorRejectsEscapesAndWrapsCanonicalResolutionFailures() {
        FileWithCanonicalMismatch mismatch = new FileWithCanonicalMismatch(
                tempDir.resolve("mismatch").toString());
        IllegalArgumentException escaped = assertThrows(IllegalArgumentException.class,
                () -> new CacheDirectory(mismatch, "feature", "cache"));
        assertTrue(escaped.getMessage().contains("escapes base folder"));

        FileWithCanonicalFailure failing = new FileWithCanonicalFailure(
                tempDir.resolve("iofail").toString());
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> new CacheDirectory(failing, "feature", "cache"));
        assertTrue(failure.getMessage().contains("Could not resolve cache directory"));
    }

    private static final class FileWithCanonicalMismatch extends java.io.File {
        @Serial
        private static final long serialVersionUID = 1L;

        private FileWithCanonicalMismatch(String pathname) {
            super(pathname);
        }

        @Override
        public java.io.File getCanonicalFile() {
            return new java.io.File(getParentFile(), "other");
        }
    }

    private static final class FileWithCanonicalFailure extends java.io.File {
        @Serial
        private static final long serialVersionUID = 1L;

        private FileWithCanonicalFailure(String pathname) {
            super(pathname);
        }

        @Override
        public java.io.File getCanonicalFile() throws IOException {
            throw new IOException("boom");
        }
    }
}
