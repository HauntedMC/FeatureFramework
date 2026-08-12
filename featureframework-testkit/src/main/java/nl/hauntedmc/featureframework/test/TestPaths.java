package nl.hauntedmc.featureframework.test;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Test path utilities.
 * Layout convention:
 * {@code <tmp>/features/<feature>/<scenario>/server-root/...}
 */
public final class TestPaths {

    private TestPaths() {
    }

    /**
     * {@code <tmp>/features/<feature>/<scenario>}
     */
    public static Path features(Path tmpRoot, String featureName, String scenario) {
        Path p = tmpRoot.resolve("features").resolve(featureName).resolve(scenario);
        mkdirs(p);
        return p;
    }

    /**
     * {@code <tmp>/features/.../server-root}
     */
    public static Path serverRoot(Path scenarioRoot) {
        Path p = scenarioRoot.resolve("server-root");
        mkdirs(p);
        return p;
    }

    /**
     * Create/return a subdirectory under {@code base}, supporting multiple segments.
     * Example: dir(serverRoot, "logs"), dir(serverRoot, "versions", "1.21.1")
     */
    public static Path dir(Path base, String first, String... more) {
        Path p = base;
        if (first != null && !first.isBlank()) p = p.resolve(first);
        if (more != null) {
            for (String s : more) {
                if (s != null && !s.isBlank()) p = p.resolve(s);
            }
        }
        mkdirs(p);
        return p;
    }

    private static void mkdirs(Path p) {
        try {
            Files.createDirectories(p);
        } catch (Exception e) {
            throw new RuntimeException("Could not create test directory: " + p, e);
        }
    }
}
