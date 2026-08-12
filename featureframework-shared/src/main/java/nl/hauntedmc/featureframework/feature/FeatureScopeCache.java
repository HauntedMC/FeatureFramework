package nl.hauntedmc.featureframework.feature;

import nl.hauntedmc.featureframework.config.FeatureStoragePaths;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/** Keeps stable, reusable resources for a feature across runtime instance reloads. */
public final class FeatureScopeCache<C, L, G> {
    private final Function<String, ? extends C> configFactory;
    private final Function<String, ? extends L> localizationFactory;
    private final Function<String, ? extends G> loggerFactory;
    private final ConcurrentHashMap<String, Scope<C, L, G>> scopes = new ConcurrentHashMap<>();

    public FeatureScopeCache(
            Function<String, ? extends C> configFactory,
            Function<String, ? extends L> localizationFactory,
            Function<String, ? extends G> loggerFactory
    ) {
        this.configFactory = Objects.requireNonNull(configFactory, "configFactory");
        this.localizationFactory = Objects.requireNonNull(localizationFactory, "localizationFactory");
        this.loggerFactory = Objects.requireNonNull(loggerFactory, "loggerFactory");
    }

    public Scope<C, L, G> scope(String featureName) {
        String key = FeatureStoragePaths.normalizeFeatureName(featureName);
        return scopes.computeIfAbsent(key, this::createScope);
    }

    public void clear() { scopes.clear(); }
    public int size() { return scopes.size(); }

    private Scope<C, L, G> createScope(String featureName) {
        return new Scope<>(
                configFactory.apply(featureName),
                localizationFactory.apply(featureName),
                loggerFactory.apply(featureName)
        );
    }

    public record Scope<C, L, G>(C config, L localization, G logger) {
        public Scope {
            Objects.requireNonNull(config, "config");
            Objects.requireNonNull(localization, "localization");
            Objects.requireNonNull(logger, "logger");
        }
    }
}
