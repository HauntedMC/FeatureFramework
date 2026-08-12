package nl.hauntedmc.featureframework.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;

class SharedBoundaryTest {
    @Test
    void sharedSourcesHaveNoPlatformKnowledge() throws IOException {
        Path sources = Path.of("src", "main", "java");
        try (var files = Files.walk(sources)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);
                assertFalse(source.contains("org.bukkit") || source.contains("io.papermc")
                                || source.contains("com.velocitypowered"),
                        () -> file + " imports a platform API");
            }
        }
    }
}
