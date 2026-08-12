package nl.hauntedmc.featureframework.config;

import nl.hauntedmc.featureframework.toolkit.io.config.ConfigMap;
import nl.hauntedmc.featureframework.toolkit.io.config.ConfigNode;
import nl.hauntedmc.featureframework.toolkit.io.config.ConfigService;
import nl.hauntedmc.featureframework.toolkit.io.config.ConfigView;

import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/** Shared root configuration behavior for a feature host. */
public abstract class FeatureConfigurationRoot<F extends FeatureConfigHandler> extends ConfigView {
    private final ConfigService service;

    protected FeatureConfigurationRoot(ConfigService service) {
        super(service.open("config.yml", true), "");
        this.service = Objects.requireNonNull(service, "service");
    }

    protected final void initializeGlobalDefaults(
            Map<String, Object> globalDefaults,
            Consumer<String> defaultAddedLogger
    ) {
        for (Map.Entry<String, Object> entry : Objects.requireNonNull(globalDefaults, "globalDefaults").entrySet()) {
            String path = "global." + entry.getKey();
            if (putIfAbsent(path, entry.getValue())) {
                defaultAddedLogger.accept(path);
            }
        }
    }

    protected final ConfigService configService() {
        return service;
    }

    protected abstract F createFeatureConfig(String normalizedFeatureName);

    public final void reloadConfig() {
        file.reload();
    }

    public final F openFeatureConfig(String featureName) {
        return createFeatureConfig(FeatureStoragePaths.normalizeFeatureName(featureName));
    }

    public final void registerFeature(String featureName) {
        registerFeature(featureName, false);
    }

    /** Registers a feature without overwriting an operator's existing enablement choice. */
    public final void registerFeature(String featureName, boolean enabledByDefault) {
        openFeatureConfig(featureName).putIfAbsent("enabled", enabledByDefault);
    }

    public final void injectFeatureDefaults(String featureName, ConfigMap defaults) {
        openFeatureConfig(featureName).injectDefaults(defaults);
    }

    public final boolean isFeatureEnabled(String featureName) {
        return openFeatureConfig(featureName).get("enabled", Boolean.class, false);
    }

    public final void setFeatureEnabled(String featureName, boolean enabled) {
        openFeatureConfig(featureName).put("enabled", enabled);
    }

    public final Object getGlobalSetting(String key) {
        return get("global." + key);
    }

    public final <T> T getGlobalSetting(String key, Class<T> type) {
        return get("global." + key, type);
    }

    public final <T> T getGlobalSetting(String key, Class<T> type, T defaultValue) {
        return get("global." + key, type, defaultValue);
    }

    public final ConfigNode globalNode(String key) {
        return node("global." + key);
    }
}
