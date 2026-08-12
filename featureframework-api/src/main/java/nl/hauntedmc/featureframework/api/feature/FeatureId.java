package nl.hauntedmc.featureframework.api.feature;

import java.util.Locale;
import java.util.Objects;

/** Stable, normalized identity of a managed feature. */
public record FeatureId(String value) implements Comparable<FeatureId> {
    public static final int MAX_LENGTH = 64;

    public FeatureId {
        Objects.requireNonNull(value, "value");
        value = value.trim().toLowerCase(Locale.ROOT);
        if (value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("Feature id exceeds " + MAX_LENGTH + " characters");
        }
        if (!isValid(value)) {
            throw new IllegalArgumentException("Invalid feature id: " + value);
        }
    }

    public static FeatureId of(String value) {
        return new FeatureId(value);
    }

    @Override
    public int compareTo(FeatureId other) {
        return value.compareTo(Objects.requireNonNull(other, "other").value);
    }

    @Override
    public String toString() {
        return value;
    }

    private static boolean isValid(String value) {
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
