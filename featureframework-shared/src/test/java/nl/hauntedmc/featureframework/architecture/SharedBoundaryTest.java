package nl.hauntedmc.featureframework.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;

class SharedBoundaryTest {
    @Test
    void sharedSourcesHaveNoPlatformKnowledge() throws IOException {
        assertSourcesExclude(
                "org.bukkit",
                "io.papermc",
                "com.velocitypowered",
                "nl.hauntedmc.featureframework.paper",
                "nl.hauntedmc.featureframework.velocity"
        );
    }

    @Test
    void hostFacadeDoesNotReabsorbLowLevelGraphMechanics() throws IOException {
        String source = Files.readString(Path.of(
                "src", "main", "java", "nl", "hauntedmc", "featureframework", "host", "FeatureHost.java"));
        assertFalse(source.contains("FeatureManifestDiscovery"));
        assertFalse(source.contains("FeatureDependencyManager"));
        assertFalse(source.contains("FeatureStartupCoordinator"));
        assertFalse(source.contains("FeatureGraphReloadTransaction"));
    }

    @Test
    void extractedHostMechanicsRemainInternalImplementationDetails() throws ClassNotFoundException {
        assertFalse(Modifier.isPublic(Class.forName(
                "nl.hauntedmc.featureframework.host.FeatureInventory").getModifiers()));
        assertFalse(Modifier.isPublic(Class.forName(
                "nl.hauntedmc.featureframework.host.FeatureInstanceController").getModifiers()));
    }

    private static void assertSourcesExclude(String... forbidden) throws IOException {
        Path sources = Path.of("src", "main", "java");
        try (var files = Files.walk(sources)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);
                for (String value : forbidden) {
                    assertFalse(source.contains(value), () -> file + " contains forbidden dependency " + value);
                }
            }
        }
    }
}
