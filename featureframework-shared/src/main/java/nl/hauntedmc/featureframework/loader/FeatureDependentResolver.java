package nl.hauntedmc.featureframework.loader;


import java.util.List;
import java.util.Set;
import java.util.function.Function;

public final class FeatureDependentResolver {

    private FeatureDependentResolver() {
    }

    public static List<String> getDependentFeatures(
            String targetKey,
            Set<String> loadedFeatureNames,
            Function<String, ? extends FeatureDescriptor<?, ?>> descriptorProvider,
            Function<String, String> featureKeyResolver
    ) {
        if (targetKey == null) {
            return List.of();
        }

        return loadedFeatureNames.stream()
                .filter(name -> {
                    FeatureDescriptor<?, ?> descriptor = descriptorProvider.apply(name);
                    if (descriptor == null) {
                        return false;
                    }
                    for (String dependency : descriptor.featureDependencies()) {
                        String dependencyKey = featureKeyResolver.apply(dependency);
                        if (targetKey.equals(dependencyKey)) {
                            return true;
                        }
                    }
                    return false;
                })
                .toList();
    }
}
