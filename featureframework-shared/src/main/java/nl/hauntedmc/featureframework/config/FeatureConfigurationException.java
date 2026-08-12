package nl.hauntedmc.featureframework.config;

import java.util.List;
import java.util.Objects;

/** Raised when persisted feature configuration is incompatible with its declared defaults. */
public final class FeatureConfigurationException extends IllegalStateException {
    private static final long serialVersionUID = 1L;
    private final String featureName;
    private final String[] mismatches;

    public FeatureConfigurationException(String featureName, List<String> mismatches) {
        super("Invalid configuration for feature '" + Objects.requireNonNull(featureName, "featureName")
                + "': " + String.join(", ", List.copyOf(mismatches))
                + ". Existing values were preserved.");
        this.featureName = featureName;
        this.mismatches = List.copyOf(mismatches).toArray(String[]::new);
    }

    public String featureName() { return featureName; }
    public List<String> mismatches() { return List.of(mismatches); }
}
