package nl.hauntedmc.featureframework.config;

import nl.hauntedmc.featureframework.toolkit.io.localization.Language;

import java.util.Objects;
import java.util.regex.Pattern;

public final class FeatureStoragePaths {

    private static final Pattern VALID_FEATURE_NAME = Pattern.compile("[A-Za-z0-9_-]+");
    private static final Pattern VALID_LOCAL_DATA_FILE =
            Pattern.compile("[A-Za-z0-9_-]+\\.ya?ml", Pattern.CASE_INSENSITIVE);

    private FeatureStoragePaths() {
    }

    public static String featureDirectory(String featureName) {
        return "features/" + normalizeFeatureName(featureName);
    }

    public static String configPath(String featureName) {
        return featureDirectory(featureName) + "/config.yml";
    }

    public static String messagesPath(String featureName) {
        return featureDirectory(featureName) + "/messages.yml";
    }

    public static String messagesPath(String featureName, Language language) {
        Objects.requireNonNull(language, "language");
        return featureDirectory(featureName) + "/" + language.getFileName();
    }

    public static String localDataPath(String fileName) {
        String normalized = Objects.requireNonNull(fileName, "fileName").trim();
        if (!isValidLocalDataFileName(normalized)) {
            throw new IllegalArgumentException("Invalid local YAML file name: " + fileName);
        }
        return "local/" + normalized;
    }

    /** Returns whether a value can be used as one feature storage directory name. */
    public static boolean isValidFeatureName(String featureName) {
        if (featureName == null) return false;
        String normalized = featureName.trim();
        return !normalized.isEmpty() && VALID_FEATURE_NAME.matcher(normalized).matches();
    }

    /** Returns whether a value is a safe local YAML file name without directory segments. */
    public static boolean isValidLocalDataFileName(String fileName) {
        return fileName != null && VALID_LOCAL_DATA_FILE.matcher(fileName.trim()).matches();
    }

    public static String normalizeFeatureName(String featureName) {
        String normalized = Objects.requireNonNull(featureName, "featureName").trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Feature name cannot be blank");
        }
        if (!VALID_FEATURE_NAME.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Invalid feature name for storage path: " + featureName);
        }
        return normalized;
    }
}
