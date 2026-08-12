package nl.hauntedmc.featureframework.paper.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;

class PaperBoundaryTest {
    @Test
    void paperAdapterHasNoVelocityKnowledge() throws IOException {
        assertSourcesExclude(
                "com.velocitypowered",
                "nl.hauntedmc.featureframework.velocity"
        );
    }

    private static void assertSourcesExclude(String... forbidden) throws IOException {
        try (var files = Files.walk(Path.of("src", "main", "java"))) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);
                for (String value : forbidden) {
                    assertFalse(source.contains(value), () -> file + " contains forbidden dependency " + value);
                }
            }
        }
    }
}
