package nl.hauntedmc.featureframework.host;

import nl.hauntedmc.featureframework.feature.Feature;
import nl.hauntedmc.featureframework.feature.FeatureScopeCache;
import nl.hauntedmc.featureframework.lifecycle.FeatureLifecycleResources;
import nl.hauntedmc.featureframework.loader.ResolvedFeatureDefinition;

import java.util.Objects;
import java.util.function.Function;

/**
 * Owns stable per-feature config/localization/logger scopes and fresh lifecycle resources per load.
 *
 * <p>The context assembler maps framework-owned values into a platform context. Standard Paper and
 * Velocity hosts provide that assembly, so products do not repeat it.</p>
 */
public final class FeatureScopeFactory<
        F extends Feature,
        C extends FeatureHostContext,
        CFG,
        LOC,
        LOG,
        R extends FeatureLifecycleResources> {

    @FunctionalInterface
    public interface ContextAssembler<F extends Feature, C, CFG, LOC, LOG, R> {
        C assemble(
                ResolvedFeatureDefinition<F, C> descriptor,
                CFG config,
                LOC localization,
                LOG logger,
                R resources
        );
    }

    private final FeatureScopeCache<CFG, LOC, LOG> scopes;
    private final Function<ResolvedFeatureDefinition<F, C>, ? extends R> resourcesFactory;
    private final ContextAssembler<F, C, CFG, LOC, LOG, R> contextAssembler;

    public FeatureScopeFactory(
            Function<String, ? extends CFG> configFactory,
            Function<String, ? extends LOC> localizationFactory,
            Function<String, ? extends LOG> loggerFactory,
            Function<ResolvedFeatureDefinition<F, C>, ? extends R> resourcesFactory,
            ContextAssembler<F, C, CFG, LOC, LOG, R> contextAssembler
    ) {
        scopes = new FeatureScopeCache<>(configFactory, localizationFactory, loggerFactory);
        this.resourcesFactory = Objects.requireNonNull(resourcesFactory, "resourcesFactory");
        this.contextAssembler = Objects.requireNonNull(contextAssembler, "contextAssembler");
    }

    public C createContext(ResolvedFeatureDefinition<F, C> descriptor) {
        Objects.requireNonNull(descriptor, "descriptor");
        FeatureScopeCache.Scope<CFG, LOC, LOG> scope = scopes.scope(descriptor.featureName());
        return contextAssembler.assemble(
                descriptor,
                scope.config(),
                scope.localization(),
                scope.logger(),
                resourcesFactory.apply(descriptor)
        );
    }

    public CFG config(String featureName) { return scopes.scope(featureName).config(); }
    public LOC localization(String featureName) { return scopes.scope(featureName).localization(); }
    public LOG logger(String featureName) { return scopes.scope(featureName).logger(); }
    public void clear() { scopes.clear(); }
    public int size() { return scopes.size(); }
}
