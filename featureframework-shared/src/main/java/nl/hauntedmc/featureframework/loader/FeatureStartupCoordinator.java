package nl.hauntedmc.featureframework.loader;

import nl.hauntedmc.featureframework.feature.stateful.FeatureReloadState;
import nl.hauntedmc.featureframework.feature.stateful.SnapshotState;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/** Atomic feature construction and startup transaction with best-effort rollback. */
public final class FeatureStartupCoordinator {
    private FeatureStartupCoordinator() { }

    public static <F, C> boolean start(
            SnapshotState reloadState,
            Supplier<C> contextFactory,
            Function<C, F> featureFactory,
            Consumer<F> prepareFeature,
            Consumer<F> initializeFeature,
            Consumer<F> activateServices,
            Consumer<F> registerFeature,
            Runnable markStarting,
            Runnable markActive,
            Consumer<Throwable> markFailed,
            Consumer<F> cleanupFeature,
            Consumer<C> cleanupContext,
            Runnable unregisterFeature
    ) {
        Objects.requireNonNull(contextFactory, "contextFactory");
        Objects.requireNonNull(featureFactory, "featureFactory");
        Objects.requireNonNull(prepareFeature, "prepareFeature");
        Objects.requireNonNull(initializeFeature, "initializeFeature");
        Objects.requireNonNull(activateServices, "activateServices");
        Objects.requireNonNull(registerFeature, "registerFeature");
        Objects.requireNonNull(markStarting, "markStarting");
        Objects.requireNonNull(markActive, "markActive");
        Objects.requireNonNull(markFailed, "markFailed");
        Objects.requireNonNull(cleanupFeature, "cleanupFeature");
        Objects.requireNonNull(cleanupContext, "cleanupContext");
        Objects.requireNonNull(unregisterFeature, "unregisterFeature");

        C context = null;
        F feature = null;
        boolean initializationStarted = false;
        try {
            markStarting.run();
            context = contextFactory.get();
            feature = featureFactory.apply(context);
            prepareFeature.accept(feature);
            initializationStarted = true;
            initializeFeature.accept(feature);
            if (reloadState != null) {
                FeatureReloadState.restore(feature, reloadState);
            }
            activateServices.accept(feature);
            registerFeature.accept(feature);
            markActive.run();
            return true;
        } catch (Throwable failure) {
            try {
                markFailed.accept(failure);
            } catch (Throwable reportingFailure) {
                failure.addSuppressed(reportingFailure);
            }
            try {
                try {
                    if (feature != null && initializationStarted) {
                        cleanupFeature.accept(feature);
                    } else if (context != null) {
                        cleanupContext.accept(context);
                    }
                } finally {
                    unregisterFeature.run();
                }
            } catch (Throwable cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            return false;
        }
    }
}
