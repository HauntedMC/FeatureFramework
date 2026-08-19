package nl.hauntedmc.featureframework.dependency;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class DependencyCheckResultTest {

    @Test
    void okIsTrueWhenNothingMissing() {
        DependencyCheckResult result = new DependencyCheckResult(Set.of(), Set.of());
        assertTrue(result.ok());
        assertFalse(result.hasMissingDependencies());
        assertEquals(0, result.missingDependencyCount());
    }

    @Test
    void okIsFalseWhenAnyDependencyMissing() {
        DependencyCheckResult result = new DependencyCheckResult(Set.of("Vault"), Set.of("OtherFeature"));
        assertFalse(result.ok());
        assertTrue(result.hasMissingDependencies());
        assertEquals(2, result.missingDependencyCount());
    }

    @Test
    void constructorCopiesAndFreezesSets() {
        LinkedHashSet<String> plugins = new LinkedHashSet<>(Set.of("A"));
        DependencyCheckResult result = new DependencyCheckResult(plugins, Set.of());
        plugins.add("B");

        assertFalse(result.missingPluginDependencies().contains("B"));
        assertThrows(UnsupportedOperationException.class, () -> result.missingPluginDependencies().add("C"));
    }
}
