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
        Path root = Path.of("src/main/java/nl/hauntedmc/featureframework/toolkit");
        List<String> violations = new ArrayList<>();
        List<String> forbiddenImports = List.of(
                "org.bukkit.",
                "io.papermc.",
                "com.velocitypowered.",
                "nl.hauntedmc.featureframework.paper.",
                "nl.hauntedmc.featureframework.velocity.",
                "nl.hauntedmc.featureframework.host.",
                "nl.hauntedmc.featureframework.lifecycle.",
                "nl.hauntedmc.featureframework.integration."
        );

        try (var files = Files.walk(root)) {
            for (Path source : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                for (String line : Files.readAllLines(source)) {
                    String trimmed = line.trim();
                    if (!trimmed.startsWith("import ")) continue;
                    for (String forbidden : forbiddenImports) {
                        if (trimmed.startsWith("import " + forbidden)) {
                            violations.add(root.relativize(source) + ": " + trimmed);
                        }
                    }
                }
            }
        }

        assertEquals(List.of(), violations,
                "Toolkit code must remain reusable without host/lifecycle/integration or platform adapters");
    }
}
