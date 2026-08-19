package nl.hauntedmc.featureframework.test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public final class TestFs {

    private TestFs() {
    }

    public static Path directory(Path directory) {
        try {
            return Files.createDirectories(directory);
        } catch (IOException e) {
            throw new RuntimeException("Could not create test directory: " + directory, e);
        }
    }

    public static Path touch(Path file) {
        try {
            Files.createDirectories(file.getParent());
            if (!Files.exists(file)) {
                Files.createFile(file);
            }
            return file;
        } catch (IOException e) {
            throw new RuntimeException("Could not create test file: " + file, e);
        }
    }

    public static Path write(Path file, String content) {
        try {
            Files.createDirectories(file.getParent());
            return Files.writeString(file, content == null ? "" : content, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("Could not write test file: " + file, e);
        }
    }

    public static String read(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Could not read test file: " + file, e);
        }
    }

    public static boolean exists(Path file) {
        return Files.exists(file);
    }
}
