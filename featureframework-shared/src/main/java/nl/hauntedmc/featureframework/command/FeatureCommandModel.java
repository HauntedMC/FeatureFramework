package nl.hauntedmc.featureframework.command;

import nl.hauntedmc.featureframework.feature.Feature;
import nl.hauntedmc.featureframework.loader.FeatureDescriptor;
import nl.hauntedmc.featureframework.loader.FeatureRegistry;

import java.util.*;
import java.util.function.Function;

/** Read-only feature administration model shared by platform command front ends. */
public final class FeatureCommandModel<F extends Feature, D extends FeatureDescriptor<F, ?>> {
    private final FeatureRegistry<F, D> registry;
    private final Function<String, String> keyResolver;

    public FeatureCommandModel(FeatureRegistry<F, D> registry, Function<String, String> keyResolver) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.keyResolver = Objects.requireNonNull(keyResolver, "keyResolver");
    }

    public Optional<FeatureInfo> info(String requestedName) {
        if (requestedName == null || requestedName.isBlank()) return Optional.empty();
        F loaded = registry.getLoadedFeature(requestedName);
        if (loaded == null) {
            loaded = registry.getLoadedFeatures().stream()
                    .filter(feature -> requestedName.equalsIgnoreCase(feature.getFeatureName()))
                    .findFirst()
                    .orElse(null);
        }
        if (loaded != null) {
            return Optional.of(new FeatureInfo(
                    Objects.toString(loaded.getFeatureName(), requestedName),
                    true,
                    Objects.toString(loaded.getFeatureVersion(), "?"),
                    safeList(loaded.getPluginDependencies()),
                    safeList(loaded.getDependencies())
            ));
        }

        String key = keyResolver.apply(requestedName);
        if (key == null) return Optional.empty();
        D descriptor = registry.getAvailableFeature(key);
        return Optional.of(descriptor == null
                ? new FeatureInfo(key, false, "?", List.of(), List.of())
                : new FeatureInfo(
                        descriptor.featureName(),
                        false,
                        descriptor.featureVersion(),
                        List.copyOf(descriptor.pluginDependencies()),
                        List.copyOf(descriptor.featureDependencies())
                ));
    }

    public List<FeatureCommandView.FeatureListEntry> loadedEntries() {
        return registry.getLoadedFeatures().stream()
                .sorted(Comparator.comparing(
                        feature -> Objects.toString(feature.getFeatureName(), ""),
                        String.CASE_INSENSITIVE_ORDER
                ))
                .map(feature -> new FeatureCommandView.FeatureListEntry(
                        Objects.toString(feature.getFeatureName(), "?"),
                        Objects.toString(feature.getFeatureVersion(), "?")
                ))
                .toList();
    }

    public List<FeatureSuggestion> loadedSuggestions(String prefix) {
        String normalizedPrefix = normalizePrefix(prefix);
        List<FeatureSuggestion> suggestions = new ArrayList<>();
        for (String key : registry.getLoadedFeatureNames()) {
            if (key == null || !key.toLowerCase(Locale.ROOT).startsWith(normalizedPrefix)) continue;
            D descriptor = registry.getAvailableFeature(key);
            F feature = registry.getLoadedFeature(key);
            suggestions.add(new FeatureSuggestion(
                    key,
                    descriptor == null
                            ? Objects.toString(feature == null ? null : feature.getFeatureName(), key)
                            : descriptor.featureName(),
                    descriptor == null
                            ? Objects.toString(feature == null ? null : feature.getFeatureVersion(), "?")
                            : descriptor.featureVersion(),
                    true
            ));
        }
        suggestions.sort(Comparator.comparing(FeatureSuggestion::key, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(suggestions);
    }

    public List<FeatureSuggestion> allSuggestions(String prefix) {
        String normalizedPrefix = normalizePrefix(prefix);
        List<FeatureSuggestion> suggestions = new ArrayList<>(loadedSuggestions(prefix));
        Set<String> included = new HashSet<>();
        suggestions.forEach(suggestion -> included.add(suggestion.key().toLowerCase(Locale.ROOT)));
        registry.getAvailableFeatures().forEach((key, descriptor) -> {
            if (key == null) return;
            String normalizedKey = key.toLowerCase(Locale.ROOT);
            if (!normalizedKey.startsWith(normalizedPrefix) || included.contains(normalizedKey)) return;
            suggestions.add(new FeatureSuggestion(
                    key,
                    descriptor == null ? key : descriptor.featureName(),
                    descriptor == null ? "?" : descriptor.featureVersion(),
                    false
            ));
        });
        suggestions.sort(Comparator.comparing(FeatureSuggestion::key, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(suggestions);
    }

    public List<String> enableCandidates(String prefix) {
        String normalizedPrefix = normalizePrefix(prefix);
        Set<String> loaded = new HashSet<>();
        registry.getLoadedFeatureNames().forEach(key -> loaded.add(key.toLowerCase(Locale.ROOT)));
        return registry.getAvailableFeatures().keySet().stream()
                .filter(Objects::nonNull)
                .filter(key -> !loaded.contains(key.toLowerCase(Locale.ROOT)))
                .filter(key -> key.toLowerCase(Locale.ROOT).startsWith(normalizedPrefix))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    private static List<String> safeList(List<String> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    private static String normalizePrefix(String prefix) {
        return prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
    }

    public record FeatureInfo(
            String name,
            boolean enabled,
            String version,
            List<String> pluginDependencies,
            List<String> featureDependencies
    ) {
    }

    public record FeatureSuggestion(String key, String displayName, String version, boolean enabled) {
    }
}
