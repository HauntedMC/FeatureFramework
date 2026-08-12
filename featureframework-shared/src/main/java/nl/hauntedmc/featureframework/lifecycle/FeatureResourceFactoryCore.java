package nl.hauntedmc.featureframework.lifecycle;

import nl.hauntedmc.featureframework.api.feature.FeatureId;
import nl.hauntedmc.featureframework.service.DefaultCapabilityRegistry;
import nl.hauntedmc.featureframework.service.FeatureServiceManager;
import nl.hauntedmc.featureframework.service.InternalServiceRegistry;
import nl.hauntedmc.featureframework.toolkit.log.FrameworkLogger;

import java.nio.file.Path;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/** Creates the platform-neutral part of every feature resource generation. */
public final class FeatureResourceFactoryCore<D> {
    private final Path dataDirectory;
    private final FrameworkLogger logger;
    private final Supplier<? extends D> dataManagerFactory;
    private final BiConsumer<? super D, String> dataManagerBinder;

    public FeatureResourceFactoryCore(
            Path dataDirectory,
            FrameworkLogger logger,
            Supplier<? extends D> dataManagerFactory,
            BiConsumer<? super D, String> dataManagerBinder
    ) {
        this.dataDirectory = Objects.requireNonNull(dataDirectory, "dataDirectory");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.dataManagerFactory = Objects.requireNonNull(dataManagerFactory, "dataManagerFactory");
        this.dataManagerBinder = Objects.requireNonNull(dataManagerBinder, "dataManagerBinder");
    }

    public Bundle<D> create(
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
        D dataManager = dataManagerFactory.get();
        if (dataManager != null) dataManagerBinder.accept(dataManager, owner);
        return new Bundle<>(owner, dataManager, new FeatureCacheManager(dataDirectory, logger), services);
    }

    public record Bundle<D>(
            String featureName,
            D dataManager,
            FeatureCacheManager cacheManager,
            FeatureServiceManager<FeatureId> serviceManager
    ) { }

    private static String requireText(String value) {
        String clean = Objects.requireNonNull(value, "featureName").trim();
        if (clean.isEmpty()) throw new IllegalArgumentException("featureName must not be blank");
        return clean;
    }
}
