package nl.hauntedmc.featureframework.api.network;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/** Validated, case-normalized backend server identity. */
public record ServerId(String value) implements Comparable<ServerId> {
    public static final int MAX_LENGTH = 64;

    public ServerId {
        Objects.requireNonNull(value, "value");
        value = normalize(value);
        if (value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("Server id exceeds " + MAX_LENGTH + " characters");
        }
        if (value.isEmpty()) {
            throw new IllegalArgumentException("server id must not be blank");
        }
        if (!hasValidCharacters(value)) {
            throw new IllegalArgumentException("Invalid server id: " + value);
        }
    }

    public static ServerId of(String value) {
        return new ServerId(value);
    }

    /** Returns whether a raw value can be normalized into a valid server id. */
    public static boolean isValid(String value) {
        if (value == null) return false;
        String normalized = normalize(value);
        return !normalized.isEmpty()
                && normalized.length() <= MAX_LENGTH
                && hasValidCharacters(normalized);
    }

    /** Parses a server id without throwing for null or malformed external input. */
    public static Optional<ServerId> tryParse(String value) {
        return isValid(value) ? Optional.of(new ServerId(value)) : Optional.empty();
    }

    public static Optional<ServerId> optional(String value) {
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(new ServerId(value));
    }

    @Override
    public int compareTo(ServerId other) {
        return value.compareTo(Objects.requireNonNull(other, "other").value);
    }

    @Override
    public String toString() {
        return value;
    }

    private static String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean hasValidCharacters(String value) {
        for (int index = 0; index < value.length(); index++) {
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
