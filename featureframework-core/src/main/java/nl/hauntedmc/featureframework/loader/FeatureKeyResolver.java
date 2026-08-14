package nl.hauntedmc.featureframework.loader;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public final class FeatureKeyResolver {

    private FeatureKeyResolver() {
    }

    public static String resolveFeatureKey(
            String inputName,
            Map<String, ? extends ResolvedFeatureDefinition<?, ?>> availableFeatures,
            Set<String> loadedFeatureNames,
            Function<String, String> loadedFeatureDisplayNameProvider
    ) {
        if (inputName == null) {
            return null;
        }

        String candidate = inputName.trim();
        if (candidate.isEmpty()) {
            return null;
        }

        if (availableFeatures.containsKey(candidate) || loadedFeatureNames.contains(candidate)) {
            return candidate;
        }

        String availableCaseMatch = findCaseInsensitiveMatch(candidate, availableFeatures.keySet());
        if (availableCaseMatch != null) {
            return availableCaseMatch;
        }
        String loadedCaseMatch = findCaseInsensitiveMatch(candidate, loadedFeatureNames);
        if (loadedCaseMatch != null) {
            return loadedCaseMatch;
        }

        for (ResolvedFeatureDefinition<?, ?> descriptor : availableFeatures.values()) {
            if (candidate.equalsIgnoreCase(descriptor.featureName())) {
                return descriptor.registryName();
            }

            String simpleClassName = descriptor.implementationType().getSimpleName();
            if (candidate.equalsIgnoreCase(simpleClassName)) {
                return descriptor.registryName();
            }
        }

        for (String loadedKey : loadedFeatureNames) {
            String loadedName = loadedFeatureDisplayNameProvider.apply(loadedKey);
            if (loadedName != null && candidate.equalsIgnoreCase(loadedName)) {
                return loadedKey;
            }
        }

        return null;
    }

    public static String findCaseInsensitiveMatch(String candidate, Collection<String> values) {
        for (String value : values) {
            if (value != null && value.equalsIgnoreCase(candidate)) {
                return value;
            }
        }
        return null;
    }

    public static boolean isValidFeatureKey(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!Character.isLetterOrDigit(c) && c != '_' && c != '-') {
                return false;
            }
        }
        return true;
    }
}
