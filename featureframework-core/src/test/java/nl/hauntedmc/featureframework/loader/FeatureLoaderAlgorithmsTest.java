package nl.hauntedmc.featureframework.loader;

import nl.hauntedmc.featureframework.api.feature.FeaturePlacement;
import nl.hauntedmc.featureframework.feature.Feature;
import nl.hauntedmc.featureframework.toolkit.io.config.ConfigMap;
import nl.hauntedmc.featureframework.toolkit.io.localization.MessageMap;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class FeatureLoaderAlgorithmsTest {

    @Test
    void resolvesKeysUsingRegistryDisplayImplementationAndLoadedNames() {
        Map<String, ResolvedFeatureDefinition<TestFeature, Object>> available = new LinkedHashMap<>();
        available.put("ChatFilter", descriptor("ChatFilter", "Chat Filter"));
        Set<String> loaded = Set.of("LoadedFeature");

        assertEquals("ChatFilter", FeatureKeyResolver.resolveFeatureKey("chatfilter", available, loaded, key -> null));
        assertEquals("ChatFilter", FeatureKeyResolver.resolveFeatureKey("Chat Filter", available, loaded, key -> null));
        assertEquals("ChatFilter", FeatureKeyResolver.resolveFeatureKey("testfeature", available, loaded, key -> null));
        assertEquals("LoadedFeature", FeatureKeyResolver.resolveFeatureKey("visible name", available, loaded, key -> "Visible Name"));
        assertNull(FeatureKeyResolver.resolveFeatureKey(" ", available, loaded, key -> null));
        assertNull(FeatureKeyResolver.resolveFeatureKey("unknown", available, loaded, key -> null));
        assertTrue(FeatureKeyResolver.isValidFeatureKey("abc_123-XYZ"));
        assertFalse(FeatureKeyResolver.isValidFeatureKey("bad.key"));
    }

    @Test
    void ordersRequiredOptionalDiamondAndIndependentFeatures() {
        Map<String, ResolvedFeatureDefinition<TestFeature, Object>> descriptors = new LinkedHashMap<>();
        descriptors.put("Root", descriptor("Root", Set.of("Left", "Right"), Set.of()));
        descriptors.put("Left", descriptor("Left", Set.of("Base"), Set.of()));
        descriptors.put("Right", descriptor("Right", Set.of("Base"), Set.of("Optional")));
        descriptors.put("Base", descriptor("Base"));
        descriptors.put("Optional", descriptor("Optional"));
        descriptors.put("Independent", descriptor("Independent"));

        FeatureLoadOrderResolver.Result result = resolve(descriptors, descriptors.keySet(), new ArrayList<>());

        assertBefore(result.loadOrder(), "Base", "Left");
        assertBefore(result.loadOrder(), "Base", "Right");
        assertBefore(result.loadOrder(), "Optional", "Right");
        assertBefore(result.loadOrder(), "Left", "Root");
        assertBefore(result.loadOrder(), "Right", "Root");
        assertTrue(result.loadOrder().contains("Independent"));
        assertTrue(result.skippedFeatures().isEmpty());
    }

    @Test
    void isolatesMissingDependenciesAndCycles() {
        Map<String, ResolvedFeatureDefinition<TestFeature, Object>> descriptors = new LinkedHashMap<>();
        descriptors.put("MissingRoot", descriptor("MissingRoot", Set.of("Absent"), Set.of()));
        descriptors.put("A", descriptor("A", Set.of("B"), Set.of()));
        descriptors.put("B", descriptor("B", Set.of("A"), Set.of()));
        descriptors.put("Healthy", descriptor("Healthy"));
        List<String> logs = new ArrayList<>();

        FeatureLoadOrderResolver.Result result = resolve(descriptors, descriptors.keySet(), logs);

        assertEquals(List.of("Healthy"), result.loadOrder());
        assertEquals(Set.of("MissingRoot", "A", "B"), result.skippedFeatures());
        assertTrue(logs.stream().anyMatch(message -> message.contains("dependency 'Absent' is unavailable")));
        assertTrue(logs.stream().anyMatch(message -> message.contains("Dependency cycle detected")));
    }

    @Test
    void traversalLoadsTransitiveDependenciesAndReportsFailures() {
        Map<String, ResolvedFeatureDefinition<TestFeature, Object>> descriptors = Map.of(
                "A", descriptor("A", Set.of("B"), Set.of()),
                "B", descriptor("B", Set.of("C"), Set.of()),
                "C", descriptor("C")
        );
        Set<String> loaded = new LinkedHashSet<>();
        AtomicInteger calls = new AtomicInteger();

        boolean result = FeatureDependencyTraversal.checkDependencies(
                "A", new LinkedHashSet<>(), new LinkedHashSet<>(), loaded::contains, descriptors::get,
                name -> name, name -> { calls.incrementAndGet(); loaded.add(name); return true; },
                message -> { }, message -> { }
        );

        assertTrue(result);
        assertEquals(2, calls.get());
        assertEquals(Set.of("B", "C"), loaded);

        List<String> warnings = new ArrayList<>();
        assertFalse(FeatureDependencyTraversal.checkDependencies(
                "A", new LinkedHashSet<>(), new LinkedHashSet<>(), name -> false, descriptors::get,
                name -> name.equals("B") ? null : name, name -> true, warnings::add, message -> { }
        ));
        assertTrue(warnings.stream().anyMatch(message -> message.contains("Missing dependency")));
    }

    @Test
    void findsLoadedDependentsByResolvedKey() {
        Map<String, ResolvedFeatureDefinition<TestFeature, Object>> descriptors = Map.of(
                "A", descriptor("A", Set.of("core"), Set.of()),
                "B", descriptor("B", Set.of("other"), Set.of()),
                "C", descriptor("C", Set.of("Core"), Set.of())
        );

        List<String> result = FeatureDependentResolver.getDependentFeatures(
                "core", Set.of("A", "B", "C"), descriptors::get, String::toLowerCase
        ).stream().sorted().toList();

        assertEquals(List.of("A", "C"), result);
        assertEquals(List.of(), FeatureDependentResolver.getDependentFeatures(
                null, Set.of("A"), descriptors::get, name -> name
        ));
    }

    @Test
    void diagnosesTransitiveFeatureAndPluginGaps() {
        Map<String, ResolvedFeatureDefinition<TestFeature, Object>> descriptors = Map.of(
                "A", descriptor("A", Set.of("B", "Absent"), Set.of()),
                "B", new ResolvedFeatureDefinition<>("B", "B", "1", TestFeature.class,
                        ignored -> new TestFeature(), Set.of("C"), Set.of(), Set.of("Vault"),
                        Set.of(), Set.of(), FeaturePlacement.ALL_NODES,
                        Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of()),
                "C", descriptor("C")
        );

        var result = FeatureDependencyDiagnostics.diagnoseDependenciesRecursively(
                "A",
                name -> descriptors.containsKey(name) ? name : null,
                descriptors::get,
                "A"::equals,
                name -> "B".equals(name) ? Set.of("Vault") : Set.of()
        );

        assertEquals(Set.of("Vault"), result.missingPluginDependencies());
        assertEquals(Set.of("B", "Absent", "C"), result.missingFeatureDependencies());
        assertEquals(Set.of("Unknown"), FeatureDependencyDiagnostics.diagnoseDependenciesRecursively(
                "Unknown", name -> null, name -> null, name -> false, name -> Set.of()
        ).missingFeatureDependencies());
    }

    private static FeatureLoadOrderResolver.Result resolve(
            Map<String, ResolvedFeatureDefinition<TestFeature, Object>> descriptors,
            Iterable<String> requested,
            List<String> logs
    ) {
        List<String> names = new ArrayList<>();
        requested.forEach(names::add);
        return FeatureLoadOrderResolver.resolveLoadOrder(
                names, descriptors::get, name -> descriptors.containsKey(name) ? name : null, logs::add
        );
    }

    private static ResolvedFeatureDefinition<TestFeature, Object> descriptor(String name) {
        return descriptor(name, Set.of(), Set.of());
    }

    private static ResolvedFeatureDefinition<TestFeature, Object> descriptor(String name, String displayName) {
        return new ResolvedFeatureDefinition<>(name, displayName, "1", TestFeature.class, ignored -> new TestFeature(),
                Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), FeaturePlacement.ALL_NODES,
                Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of());
    }

    private static ResolvedFeatureDefinition<TestFeature, Object> descriptor(
            String name,
            Set<String> required,
            Set<String> optional
    ) {
        return new ResolvedFeatureDefinition<>(name, name, "1", TestFeature.class, ignored -> new TestFeature(),
                required, optional, Set.of(), Set.of(), Set.of(), FeaturePlacement.ALL_NODES,
                Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of());
    }

    private static void assertBefore(List<String> order, String dependency, String dependent) {
        assertTrue(order.indexOf(dependency) >= 0 && order.indexOf(dependency) < order.indexOf(dependent),
                () -> dependency + " must load before " + dependent + ": " + order);
    }

    private static final class TestFeature implements Feature {
        @Override public String name() { return "test"; }
        @Override public String version() { return "1"; }
        @Override public List<String> dependencies() { return List.of(); }
        @Override public List<String> pluginDependencies() { return List.of(); }
        @Override public ConfigMap defaultConfig() { return new ConfigMap(); }
        @Override public MessageMap defaultMessages() { return new MessageMap(); }
        @Override public void initialize() { }
        @Override public void disable() { }
    }
}
