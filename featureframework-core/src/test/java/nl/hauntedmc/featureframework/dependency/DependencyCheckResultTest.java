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
    }

    @Test
    void okIsFalseWhenAnyDependencyMissing() {
        assertFalse(new DependencyCheckResult(Set.of("Vault"), Set.of()).ok());
        assertFalse(new DependencyCheckResult(Set.of(), Set.of("OtherFeature")).ok());
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
