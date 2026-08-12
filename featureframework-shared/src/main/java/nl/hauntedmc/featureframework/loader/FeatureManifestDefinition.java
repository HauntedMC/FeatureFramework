package nl.hauntedmc.featureframework.loader;

import nl.hauntedmc.featureframework.api.feature.FeatureClassification;
import nl.hauntedmc.featureframework.api.feature.FeatureRole;

import java.util.EnumSet;
import java.util.Set;

/** Feature-independent metadata required to validate and materialize a host's explicit inventory. */
public interface FeatureManifestDefinition<D extends FeatureDescriptor<?, ?>> {
    String featureName();

    int startupOrder();

    D descriptor(Set<String> requiredFeatureDependencies);

    Set<Class<?>> requiredCapabilities();

    Set<Class<?>> optionalCapabilities();

    Set<Class<?>> providedCapabilities();

    Set<Class<?>> requiredInternalServices();

    Set<Class<?>> optionalInternalServices();

    Set<Class<?>> providedInternalServices();

    default FeatureClassification classification() {
        return FeatureClassification.INTERNAL;
    }

    default Set<FeatureRole> roles() {
        EnumSet<FeatureRole> roles = EnumSet.of(FeatureRole.OPERATOR_FACING);
        if (!providedCapabilities().isEmpty()) roles.add(FeatureRole.CAPABILITY_PROVIDER);
        if (!requiredCapabilities().isEmpty() || !optionalCapabilities().isEmpty()) {
            roles.add(FeatureRole.CAPABILITY_CONSUMER);
        }
        if (classification() == FeatureClassification.EXTENSION_PROVIDER) {
            roles.add(FeatureRole.EXTENSION_PROVIDER);
        }
        return Set.copyOf(roles);
    }
}
