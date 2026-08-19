package nl.hauntedmc.featureframework.api.feature;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/** Stable, normalized identity of a managed feature. */
public record FeatureId(String value) implements Comparable<FeatureId> {
    public static final int MAX_LENGTH = 64;

    public FeatureId {
        Objects.requireNonNull(value, "value");
        value = normalize(value);
        if (value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("Feature id exceeds " + MAX_LENGTH + " characters");
        }
        if (!isNormalizedValueValid(value)) {
            throw new IllegalArgumentException("Invalid feature id: " + value);
        }
    }

    public static FeatureId of(String value) {
        return new FeatureId(value);
    }

    /**
     * Returns whether a raw value can be normalized into a valid feature id.
     *
     * <p>This applies the same trimming and case normalization as {@link #of(String)} without
     * throwing for null or malformed input.</p>
     */
    public static boolean isValid(String value) {
        if (value == null) return false;
        String normalized = normalize(value);
        return normalized.length() <= MAX_LENGTH && isNormalizedValueValid(normalized);
    }

    /** Parses a feature id without throwing for null or malformed external input. */
    public static Optional<FeatureId> tryParse(String value) {
        return isValid(value) ? Optional.of(new FeatureId(value)) : Optional.empty();
    }

    @Override
    public int compareTo(FeatureId other) {
        return value.compareTo(Objects.requireNonNull(other, "other").value);
    }

    @Override
    public String toString() {
        return value;
    }

    private static String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean isNormalizedValueValid(String value) {
        if (value.isEmpty() || !Character.isLetterOrDigit(value.charAt(0))) {
            return false;
        }
        for (int index = 1; index < value.length(); index++) {
            char character = value.charAt(index);
            if (!Character.isLetterOrDigit(character)
                    && character != '-'
                    && character != '_'
                    && character != '.') {
                return false;
            }
        }
        return true;
    }
}
