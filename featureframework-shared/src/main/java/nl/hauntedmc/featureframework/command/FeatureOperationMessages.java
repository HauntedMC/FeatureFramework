package nl.hauntedmc.featureframework.command;

import nl.hauntedmc.featureframework.operation.disable.FeatureDisableResponse;
import nl.hauntedmc.featureframework.operation.enable.FeatureEnableResponse;
import nl.hauntedmc.featureframework.operation.reload.FeatureReloadResponse;
import nl.hauntedmc.featureframework.operation.softreload.FeatureSoftReloadResponse;

import java.util.LinkedHashMap;
import java.util.Map;

/** Converts framework operation results into localization keys and placeholders. */
public final class FeatureOperationMessages {
    private FeatureOperationMessages() { }

    public static Message enable(String feature, FeatureEnableResponse response) {
        return switch (response.result()) {
            case SUCCESS -> message("command.enable.success", feature);
            case NOT_FOUND -> message("command.enable.not_found", feature);
            case ALREADY_LOADED -> message("command.enable.already_loaded", feature);
            case MISSING_PLUGIN_DEPENDENCY -> message(
                    "command.enable.missing_plugin_dependency", feature,
                    "plugins", String.join(", ", response.missingPlugins()));
            case MISSING_FEATURE_DEPENDENCY -> message(
                    "command.enable.missing_feature_dependency", feature,
                    "features", String.join(", ", response.missingFeatures()));
            case FAILED -> message("command.enable.failed", feature);
        };
    }

    public static Message disable(String feature, FeatureDisableResponse response) {
        return switch (response.result()) {
            case SUCCESS -> response.alsoDisabledDependents().isEmpty()
                    ? message("command.disable.success", feature)
                    : message("command.disable.success_with_dependents", feature,
                            "dependents", String.join(", ", response.alsoDisabledDependents()));
            case NOT_LOADED -> message("command.disable.not_loaded", feature);
            case FAILED -> message("command.disable.failed", feature);
        };
    }

    public static Message softReload(String feature, FeatureSoftReloadResponse response) {
        return switch (response.result()) {
            case SUCCESS -> message("command.softreload.success", feature);
            case NOT_LOADED -> message("command.softreload.not_loaded", feature);
            case FAILED -> message("command.softreload.failed", feature);
        };
    }

    public static Message reload(String feature, FeatureReloadResponse response) {
        return switch (response.result()) {
            case SUCCESS -> response.reloadedDependents().isEmpty()
                    ? message("command.reload.success", feature)
                    : message("command.reload.success_with_dependents", feature,
                            "dependents", String.join(", ", response.reloadedDependents()));
            case NOT_LOADED -> message("command.reload.not_loaded", feature);
            case FAILED -> message("command.reload.failed", feature);
        };
    }

    private static Message message(String key, String feature) {
        return new Message(key, Map.of("feature", feature));
    }

    private static Message message(String key, String feature, String extraKey, String extraValue) {
        Map<String, String> placeholders = new LinkedHashMap<>();
        placeholders.put("feature", feature);
        placeholders.put(extraKey, extraValue);
        return new Message(key, placeholders);
    }

    public record Message(String key, Map<String, String> placeholders) {
        public Message {
            placeholders = Map.copyOf(placeholders);
        }
    }
}
