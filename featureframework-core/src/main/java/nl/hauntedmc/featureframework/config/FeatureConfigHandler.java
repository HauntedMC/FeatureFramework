package nl.hauntedmc.featureframework.config;

import nl.hauntedmc.featureframework.toolkit.io.config.ConfigMap;
import nl.hauntedmc.featureframework.toolkit.io.config.ConfigNode;
import nl.hauntedmc.featureframework.toolkit.io.config.ConfigService;
import nl.hauntedmc.featureframework.toolkit.io.config.ConfigView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/** Platform-neutral feature configuration stored at {@code features/<feature>/config.yml}. */
public class FeatureConfigHandler extends ConfigView {
    public enum TypeMismatchPolicy { REJECT, RESET_TO_DEFAULT }

    private final String featureName;
    private final ConfigView globalView;
    private final TypeMismatchPolicy mismatchPolicy;
    private final Consumer<String> infoLogger;
    private final Consumer<String> errorLogger;
    private final List<Runnable> reloadListeners = new CopyOnWriteArrayList<>();

    public FeatureConfigHandler(
            ConfigService service,
            ConfigView globalView,
            String featureName,
            TypeMismatchPolicy mismatchPolicy,
            Consumer<String> infoLogger,
            Consumer<String> errorLogger
    ) {
        super(service.open(FeatureStoragePaths.configPath(featureName), false), "");
        this.featureName = FeatureStoragePaths.normalizeFeatureName(featureName);
        this.globalView = Objects.requireNonNull(globalView, "globalView");
        this.mismatchPolicy = Objects.requireNonNull(mismatchPolicy, "mismatchPolicy");
        this.infoLogger = Objects.requireNonNull(infoLogger, "infoLogger");
        this.errorLogger = Objects.requireNonNull(errorLogger, "errorLogger");
    }

    public void reloadConfig() {
        file.reload();
        reloadListeners.forEach(Runnable::run);
    }

    public void registerReloadListener(Runnable listener) {
        reloadListeners.add(Objects.requireNonNull(listener, "listener"));
    }

    /** Registers a reload listener that can be removed independently by closing the returned handle. */
    public AutoCloseable subscribeReload(Runnable listener) {
        Runnable required = Objects.requireNonNull(listener, "listener");
        reloadListeners.add(required);
        return () -> reloadListeners.remove(required);
    }

    public void clearReloadListeners() {
        reloadListeners.clear();
    }

    public String featureName() { return featureName; }

    public void injectDefaults(ConfigMap defaults) {
        if (defaults == null) return;
        List<String> mismatches = findTypeMismatches(defaults);
        if (!mismatches.isEmpty() && mismatchPolicy == TypeMismatchPolicy.REJECT) {
            errorLogger.accept("Feature '" + featureName + "' has incompatible persisted values; values were preserved: "
                    + String.join(", ", mismatches));
            throw new FeatureConfigurationException(featureName, mismatches);
        }
        if (!mismatches.isEmpty()) {
            for (String topKey : mismatchedKeys(defaults)) {
                remove(topKey);
                infoLogger.accept("Removed key '" + topKey + "' from feature '" + featureName + "' due to schema change");
            }
        }
        ConfigDefaultsMerger.mergeMissingPaths(this, defaults.toMap()).forEach(key ->
                infoLogger.accept("Added missing key '" + key + "' for feature '" + featureName + "'"));
    }

    @Override public ConfigView globals() { return globalView; }
    public Object getGlobalSetting(String key) { return globals().get(key); }
    public <T> T getGlobalSetting(String key, Class<T> type) { return globals().get(key, type); }
    public <T> T getGlobalSetting(String key, Class<T> type, T def) { return globals().get(key, type, def); }
    public ConfigNode globalNode(String key) { return globals().node(key); }

    private List<String> findTypeMismatches(ConfigMap defaults) {
        List<String> mismatches = new ArrayList<>();
        for (String key : node().keys()) {
            Kind expected = expectedKind(key, defaults);
            Kind actual = classify(node(key).raw());
            if (expected != null && actual != null && expected != actual) {
                mismatches.add("'" + key + "' expected " + expected + " but found " + actual);
            }
        }
        return mismatches;
    }

    private List<String> mismatchedKeys(ConfigMap defaults) {
        return node().keys().stream().filter(key -> {
            Kind expected = expectedKind(key, defaults);
            Kind actual = classify(node(key).raw());
            return expected != null && actual != null && expected != actual;
        }).toList();
    }

    private static Kind expectedKind(String topKey, ConfigMap defaults) {
        if (defaults.contains(topKey)) return classify(defaults.get(topKey));
        String prefix = topKey + ".";
        return defaults.keySet().stream().anyMatch(key -> key.startsWith(prefix)) ? Kind.MAP : null;
    }

    private static Kind classify(Object value) {
        if (value == null) return null;
        if (value instanceof Map) return Kind.MAP;
        if (value instanceof List) return Kind.LIST;
        if (value instanceof Boolean) return Kind.BOOLEAN;
        if (value instanceof Number) return Kind.NUMBER;
        if (value instanceof CharSequence) return Kind.STRING;
        return Kind.OTHER;
    }

    private enum Kind { MAP, LIST, BOOLEAN, NUMBER, STRING, OTHER }
}
