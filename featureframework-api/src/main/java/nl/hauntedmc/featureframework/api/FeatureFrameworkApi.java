package nl.hauntedmc.featureframework.api;

import nl.hauntedmc.featureframework.api.feature.FeatureCatalog;
import nl.hauntedmc.featureframework.api.service.CapabilityRegistry;

import java.util.concurrent.CompletionStage;

/** Stable root API exposed by a FeatureFramework host for the lifetime of its plugin. */
public interface FeatureFrameworkApi<V> {

    /** Returns the host API and runtime version. */
    V version();

    /** Returns the current root-runtime lifecycle state. */
    RuntimeState state();

    /** Completes once the initial feature graph is ready. */
    CompletionStage<Void> whenReady();

    /** Returns the live, read-only capability catalog. */
    CapabilityRegistry capabilities();

    /** Returns the live, read-only feature catalog. */
    FeatureCatalog features();
}
