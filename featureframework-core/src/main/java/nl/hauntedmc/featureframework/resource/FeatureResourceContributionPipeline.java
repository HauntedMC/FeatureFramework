package nl.hauntedmc.featureframework.resource;

import nl.hauntedmc.featureframework.lifecycle.FeatureLifecycleResources;

import java.util.List;
import java.util.Objects;

/**
 * Applies declared resource contributors to one feature generation and validates its requirements.
 *
 * <p>Platform factories create their native resource scopes, while this shared pipeline keeps the
 * declaration-driven contribution and rollback contract identical on every platform.</p>
 */
public final class FeatureResourceContributionPipeline {
    private FeatureResourceContributionPipeline() {
    }

    /**
     * Applies contributors requested by {@code request}, verifies required extensions, and returns
     * {@code resources}. On failure, the resource generation is cleaned up before the failure is
     * rethrown.
     */
    public static <R extends FeatureLifecycleResources> R apply(
            FeatureResourceRequest request,
            List<? extends FeatureResourceContributor<R>> contributors,
            R resources,
            FeatureResourceExtensions extensions
    ) {
        FeatureResourceRequest requiredRequest = Objects.requireNonNull(request, "request");
        List<? extends FeatureResourceContributor<R>> requiredContributors = List.copyOf(
                Objects.requireNonNull(contributors, "contributors"));
        R requiredResources = Objects.requireNonNull(resources, "resources");
        FeatureResourceExtensions requiredExtensions = Objects.requireNonNull(extensions, "extensions");

        try {
            requiredContributors.stream()
                    .filter(contributor -> requiredRequest.requests(contributor.extensionType()))
                    .forEach(contributor -> contributor.contribute(requiredRequest, requiredResources));
            for (Class<?> extensionType : requiredRequest.requiredExtensions()) {
                if (!requiredExtensions.contains(extensionType)) {
                    throw new IllegalStateException("Feature " + requiredRequest.id().value()
                            + " requires resource extension " + extensionType.getName()
                            + ", but its host did not contribute it");
                }
            }
            return requiredResources;
        } catch (Throwable failure) {
            try {
                requiredResources.cleanup();
            } catch (Throwable cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            if (failure instanceof RuntimeException runtime) throw runtime;
            if (failure instanceof Error error) throw error;
            throw new IllegalStateException(
                    "Could not contribute resources for " + requiredRequest.id().value(), failure);
        }
    }
}
