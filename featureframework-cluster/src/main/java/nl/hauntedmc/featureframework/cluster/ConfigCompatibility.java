package nl.hauntedmc.featureframework.cluster;

import java.util.Objects;

/** Application binary version plus its independently evolving configuration compatibility version. */
public record ConfigCompatibility(
        String applicationVersion,
        String configCompatibilityVersion
) {
    public ConfigCompatibility {
        applicationVersion = text(applicationVersion, "applicationVersion");
        configCompatibilityVersion = text(configCompatibilityVersion, "configCompatibilityVersion");
    }

    public boolean isCompatible(ConfigManifest manifest) {
        return configCompatibilityVersion.equals(
                Objects.requireNonNull(manifest, "manifest").configCompatibilityVersion());
    }

    private static String text(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " must not be blank");
        return normalized;
    }
}
