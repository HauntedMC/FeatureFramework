package nl.hauntedmc.featureframework.loader;

import nl.hauntedmc.featureframework.feature.Feature;

import java.util.*;

/** Thread-safe registry of available descriptors and currently loaded feature instances. */
public class FeatureRegistry<F extends Feature, D extends FeatureDescriptor<F, ?>> {
    private final Map<String, F> loadedFeatures = new LinkedHashMap<>();
    private final Map<String, D> availableFeatures = new LinkedHashMap<>();

    public synchronized void registerAvailableFeature(D descriptor) {
        D required = Objects.requireNonNull(descriptor, "descriptor");
        availableFeatures.put(required.registryName(), required);
    }

    public synchronized void deregisterAvailableFeature(String featureName) {
        availableFeatures.remove(featureName);
    }

    public synchronized void registerLoadedFeature(String featureName, F feature) {
        loadedFeatures.put(
                Objects.requireNonNull(featureName, "featureName"),
                Objects.requireNonNull(feature, "feature")
        );
    }

    public synchronized void deregisterLoadedFeature(String featureName) {
        loadedFeatures.remove(featureName);
    }

    public synchronized F getLoadedFeature(String featureName) {
        return loadedFeatures.get(featureName);
    }

    public synchronized Set<String> getLoadedFeatureNames() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(loadedFeatures.keySet()));
    }

    public synchronized boolean isFeatureLoaded(String featureName) {
        return loadedFeatures.containsKey(featureName);
    }

    public synchronized Map<String, D> getAvailableFeatures() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(availableFeatures));
    }

    public synchronized D getAvailableFeature(String featureName) {
        return availableFeatures.get(featureName);
    }

    public synchronized List<F> getLoadedFeatures() {
        return new ArrayList<>(loadedFeatures.values());
    }
}
