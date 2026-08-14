package nl.hauntedmc.featureframework.toolkit;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ToolkitBoundaryTest {

    @Test
    void toolkitRemainsIndependentOfPlatformAndFeatureRuntimeCode() throws IOException {
        Path root = Path.of("src/main/java");
        List<String> violations = new ArrayList<>();

        try (var files = Files.walk(root)) {
            for (Path source : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                for (String line : Files.readAllLines(source)) {
                    String trimmed = line.trim();
                    if (trimmed.startsWith("import org.bukkit.")
                            || trimmed.startsWith("import io.papermc.")
                            || trimmed.startsWith("import com.velocitypowered.")
                            || trimmed.startsWith("import nl.hauntedmc.featureframework.host.")
                            || trimmed.startsWith("import nl.hauntedmc.featureframework.feature.")
                            || trimmed.startsWith("import nl.hauntedmc.featureframework.loader.")
                            || trimmed.startsWith("import nl.hauntedmc.featureframework.lifecycle.")) {
                        violations.add(root.relativize(source) + ": " + trimmed);
                    }
                }
            }
        }

        assertEquals(List.of(), violations,
                "Toolkit code must remain reusable without FeatureFramework' Paper runtime or feature implementations");
    }
}
