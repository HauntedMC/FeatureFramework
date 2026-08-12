package nl.hauntedmc.featureframework.feature;

import nl.hauntedmc.featureframework.config.FeatureConfigHandler;
import nl.hauntedmc.featureframework.lifecycle.CleanupSequence;
import nl.hauntedmc.featureframework.lifecycle.FeatureLifecycleResources;

/**
 * Shared shutdown policy for a feature with framework-owned resources.
 *
 * <p>Ingress is quiesced and callable services are withdrawn before {@link #disable()} runs, so
 * feature implementations never release state while they are still externally reachable.</p>
 *
 * @param <C> feature runtime context type
 */
public abstract class LifecycleFeature<C extends FeatureRuntimeContext> extends AbstractFeature<C> {
    protected LifecycleFeature(C context) {
        super(context);
    }

    public FeatureConfigHandler getConfigHandler() {
        return getContext().configHandler();
    }

    protected FeatureLifecycleResources lifecycleResources() {
        return getContext().lifecycle();
    }

    /** Allows a platform binding to log or otherwise announce feature shutdown. */
    protected void onCleanupStarted() {
    }

    /** Allows a platform binding to release resources that precede lifecycle quiescing. */
    protected void beforeLifecycleQuiesce() {
    }

    /** Applies the framework shutdown order for every managed feature. */
    public void cleanup() {
        onCleanupStarted();
        CleanupSequence.run(
                getConfigHandler()::clearReloadListeners,
                this::beforeLifecycleQuiesce,
                lifecycleResources()::quiesce,
                getContext()::deactivateServices,
                this::disable,
                lifecycleResources()::cleanup
        );
    }
}
