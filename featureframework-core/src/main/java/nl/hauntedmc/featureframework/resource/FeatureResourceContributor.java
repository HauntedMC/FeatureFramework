package nl.hauntedmc.featureframework.resource;

import nl.hauntedmc.featureframework.lifecycle.FeatureLifecycleResources;

/** Adds one declared resource extension to a newly-created feature generation. */
public interface FeatureResourceContributor<R extends FeatureLifecycleResources> {
    /** The extension type installed by this contributor. */
    Class<?> extensionType();

    /** Installs the extension for a feature that explicitly declared {@link #extensionType()}. */
    void contribute(FeatureResourceRequest request, R resources);
}
