package nl.hauntedmc.featureframework.loader;

import nl.hauntedmc.featureframework.api.feature.FeaturePlacement;
import nl.hauntedmc.featureframework.api.feature.FeatureRole;
import nl.hauntedmc.featureframework.api.feature.FeatureStartupPhase;
import nl.hauntedmc.featureframework.api.feature.FeatureScope;

import java.util.EnumSet;
import java.util.Set;

/** Feature-independent metadata required to validate and materialize a host's explicit inventory. */
public interface FeatureManifestDefinition<D extends ResolvedFeatureDefinition<?, ?>> {
    String featureName();

    FeatureStartupPhase startupPhase();

    FeatureScope scope();

    default FeaturePlacement placement() { return FeaturePlacement.ALL_NODES; }

    D descriptor(Set<String> requiredFeatureDependencies);

    Set<Class<?>> requiredCapabilities();

    Set<Class<?>> optionalCapabilities();

    Set<Class<?>> providedCapabilities();

    Set<Class<?>> requiredInternalServices();

    Set<Class<?>> optionalInternalServices();

    Set<Class<?>> providedInternalServices();

    default Set<Class<?>> requiredResourceExtensions() { return Set.of(); }

    default Set<Class<?>> optionalResourceExtensions() { return Set.of(); }

    default Set<FeatureRole> roles() {
        EnumSet<FeatureRole> roles = EnumSet.noneOf(FeatureRole.class);
        if (!providedCapabilities().isEmpty()) roles.add(FeatureRole.CAPABILITY_PROVIDER);
        if (!requiredCapabilities().isEmpty() || !optionalCapabilities().isEmpty()) {
            roles.add(FeatureRole.CAPABILITY_CONSUMER);
        }
        return Set.copyOf(roles);
    }
}
