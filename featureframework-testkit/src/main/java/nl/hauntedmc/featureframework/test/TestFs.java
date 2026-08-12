package nl.hauntedmc.featureframework.test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public final class TestFs {

    private TestFs() {
    }

    public static Path touch(Path file) {
        try {
            Files.createDirectories(file.getParent());
            if (!Files.exists(file)) {
                Files.createFile(file);
            }
            return file;
        } catch (IOException e) {
            throw new RuntimeException("Kon testbestand niet aanmaken: " + file, e);
        }
    }

    public static Path write(Path file, String content) {
        try {
            Files.createDirectories(file.getParent());
            return Files.writeString(file, content == null ? "" : content, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("Kon testbestand niet schrijven: " + file, e);
        }
    }

    public static boolean exists(Path file) {
        return Files.exists(file);
    }
}
