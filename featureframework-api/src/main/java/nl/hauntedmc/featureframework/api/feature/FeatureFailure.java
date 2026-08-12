package nl.hauntedmc.featureframework.api.feature;

import java.util.Objects;
import java.util.Optional;

/** Sanitized failure information safe to expose to external integrations. */
public record FeatureFailure(String phase, String code, Optional<String> message) {
    public FeatureFailure {
        phase = text(phase, "phase");
        code = text(code, "code");
        message = message == null ? Optional.empty() : message.map(value -> text(value, "message"));
    }

    private static String text(String value, String name) {
        Objects.requireNonNull(value, name);
        String normalized = value.trim();
        if (normalized.isEmpty() || normalized.length() > 160) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        return normalized;
    }
}
