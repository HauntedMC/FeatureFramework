package nl.hauntedmc.featureframework.lifecycle;

import nl.hauntedmc.featureframework.api.feature.FeatureId;
import nl.hauntedmc.featureframework.service.DefaultCapabilityRegistry;
import nl.hauntedmc.featureframework.service.FeatureServiceManager;
import nl.hauntedmc.featureframework.service.InternalServiceRegistry;
import nl.hauntedmc.featureframework.resource.FeatureResourceExtensions;
import nl.hauntedmc.featureframework.resource.FeatureResourceOwner;
import nl.hauntedmc.featureframework.toolkit.log.FrameworkLogger;

import java.nio.file.Path;
import java.util.Objects;

/** Creates the platform-neutral part of every feature resource generation. */
public final class FeatureResourceFactoryCore {
    private final Path dataDirectory;
    private final FrameworkLogger logger;

    public FeatureResourceFactoryCore(
            Path dataDirectory,
            FrameworkLogger logger
    ) {
        this.dataDirectory = Objects.requireNonNull(dataDirectory, "dataDirectory");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public Bundle create(
            String featureName,
            DefaultCapabilityRegistry capabilities,
            InternalServiceRegistry<FeatureId> internalServices
    ) {
        String owner = requireText(featureName);
        FeatureServiceManager<FeatureId> services = new FeatureServiceManager<>();
        services.bindRegistries(
                Objects.requireNonNull(capabilities, "capabilities"),
                Objects.requireNonNull(internalServices, "internalServices"),
                FeatureId.of(owner));
        return new Bundle(owner, new FeatureCacheManager(dataDirectory, logger), services,
                new FeatureResourceOwner(), new FeatureResourceExtensions());
    }

    public record Bundle(
            String featureName,
            FeatureCacheManager cacheManager,
            FeatureServiceManager<FeatureId> serviceManager,
            FeatureResourceOwner ownership,
            FeatureResourceExtensions extensions
    ) { }

    private static String requireText(String value) {
        String clean = Objects.requireNonNull(value, "featureName").trim();
        if (clean.isEmpty()) throw new IllegalArgumentException("featureName must not be blank");
        return clean;
    }
}
