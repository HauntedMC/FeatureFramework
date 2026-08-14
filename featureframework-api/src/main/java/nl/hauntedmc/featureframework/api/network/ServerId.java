package nl.hauntedmc.featureframework.api.network;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/** Validated, case-normalized backend server identity. */
public record ServerId(String value) implements Comparable<ServerId> {
    public static final int MAX_LENGTH = 64;

    public ServerId {
        Objects.requireNonNull(value, "value");
        value = value.trim().toLowerCase(Locale.ROOT);
        if (value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("Server id exceeds " + MAX_LENGTH + " characters");
        }
        if (value.isEmpty()) {
            throw new IllegalArgumentException("server id must not be blank");
        }
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (!Character.isLetterOrDigit(character)
                    && character != '-'
                    && character != '_'
                    && character != '.') {
                throw new IllegalArgumentException("Invalid server id: " + value);
            }
        }
    }

    public static ServerId of(String value) {
        return new ServerId(value);
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
}
