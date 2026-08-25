package nl.hauntedmc.featureframework.api;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;

class ObservationBoundaryTest {

    @Test
    void publicApiHasNoTelemetryVendorOrHauntedObservabilityDependency() throws IOException {
        Path sources = Path.of("src", "main", "java");
        try (var files = Files.walk(sources)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);
                assertFalse(source.contains("io.opentelemetry"), () -> file + " contains OpenTelemetry coupling");
                assertFalse(
                        source.contains("hauntedobservability"),
                        () -> file + " contains HauntedObservability coupling"
                );
            }
        }
    }
}
