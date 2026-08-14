package nl.hauntedmc.featureframework.dependency;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public record DependencyCheckResult(Set<String> missingPluginDependencies, Set<String> missingFeatureDependencies) {
    public DependencyCheckResult(Set<String> missingPluginDependencies, Set<String> missingFeatureDependencies) {
        this.missingPluginDependencies = Collections.unmodifiableSet(new LinkedHashSet<>(missingPluginDependencies));
        this.missingFeatureDependencies = Collections.unmodifiableSet(new LinkedHashSet<>(missingFeatureDependencies));
    }

    public boolean ok() {
        return missingPluginDependencies.isEmpty() && missingFeatureDependencies.isEmpty();
    }
}
