package nl.hauntedmc.featureframework.command;

import nl.hauntedmc.featureframework.api.feature.FeatureCatalog;
import nl.hauntedmc.featureframework.api.feature.FeatureId;
import nl.hauntedmc.featureframework.api.feature.FeatureSnapshot;
import nl.hauntedmc.featureframework.api.feature.FeatureState;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Read-only feature administration model backed only by the public feature catalog. */
public final class FeatureCommandModel {
    private final FeatureCatalog catalog;

    public FeatureCommandModel(FeatureCatalog catalog) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
    }

    public Optional<FeatureInfo> info(String requestedName) {
        return find(requestedName).map(snapshot -> new FeatureInfo(
                snapshot.metadata().displayName(),
                loaded(snapshot),
                snapshot.metadata().version(),
                sorted(snapshot.metadata().requiredPlugins()),
                snapshot.metadata().requiredFeatures().stream().map(FeatureId::value).sorted(String.CASE_INSENSITIVE_ORDER).toList()
        ));
    }

    public List<FeatureListEntry> loadedEntries() {
        return catalog.snapshot().stream()
                .filter(FeatureCommandModel::loaded)
                .sorted(byId())
                .map(snapshot -> new FeatureListEntry(
                        snapshot.metadata().displayName(), snapshot.metadata().version()))
                .toList();
    }

    public List<FeatureSuggestion> loadedSuggestions(String prefix) {
        return suggestions(prefix, true);
    }

    public List<FeatureSuggestion> allSuggestions(String prefix) {
        return suggestions(prefix, false);
    }

    public List<String> enableCandidates(String prefix) {
        String normalizedPrefix = normalizePrefix(prefix);
        return catalog.snapshot().stream()
                .filter(snapshot -> !loaded(snapshot))
                .map(snapshot -> snapshot.metadata().id().value())
                .filter(key -> key.toLowerCase(Locale.ROOT).startsWith(normalizedPrefix))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    private List<FeatureSuggestion> suggestions(String prefix, boolean loadedOnly) {
        String normalizedPrefix = normalizePrefix(prefix);
        return catalog.snapshot().stream()
                .filter(snapshot -> !loadedOnly || loaded(snapshot))
                .filter(snapshot -> snapshot.metadata().id().value().toLowerCase(Locale.ROOT).startsWith(normalizedPrefix))
                .sorted(byId())
                .map(snapshot -> new FeatureSuggestion(
                        snapshot.metadata().id().value(),
                        snapshot.metadata().displayName(),
                        snapshot.metadata().version(),
                        loaded(snapshot)))
                .toList();
    }

    private Optional<FeatureSnapshot> find(String requestedName) {
        if (requestedName == null || requestedName.isBlank()) return Optional.empty();
        return catalog.snapshot().stream().filter(snapshot ->
                snapshot.metadata().id().value().equalsIgnoreCase(requestedName)
                        || snapshot.metadata().displayName().equalsIgnoreCase(requestedName))
                .findFirst();
    }

    private static boolean loaded(FeatureSnapshot snapshot) {
        return snapshot.state() == FeatureState.ACTIVE;
    }

    private static Comparator<FeatureSnapshot> byId() {
        return Comparator.comparing(snapshot -> snapshot.metadata().id().value(), String.CASE_INSENSITIVE_ORDER);
    }

    private static List<String> sorted(Set<String> values) {
        return values.stream().sorted(String.CASE_INSENSITIVE_ORDER).toList();
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
    ) { }

    public record FeatureSuggestion(String key, String displayName, String version, boolean enabled) { }

    public record FeatureListEntry(String name, String version) { }
}
