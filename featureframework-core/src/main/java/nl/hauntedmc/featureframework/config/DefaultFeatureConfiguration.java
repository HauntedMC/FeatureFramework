package nl.hauntedmc.featureframework.config;

import nl.hauntedmc.featureframework.toolkit.io.config.ConfigService;
import nl.hauntedmc.featureframework.toolkit.log.FrameworkLogger;

import java.util.Map;
import java.util.Objects;

/** Ready-to-use root configuration for plugins that do not need a product-specific subclass. */
public final class DefaultFeatureConfiguration extends FeatureConfigurationRoot<FeatureConfigHandler> {
    private final FeatureConfigHandler.TypeMismatchPolicy mismatchPolicy;
    private final FrameworkLogger logger;

    public DefaultFeatureConfiguration(ConfigService service, FrameworkLogger logger) {
        this(service, logger, FeatureConfigHandler.TypeMismatchPolicy.REJECT, Map.of());
    }

    public DefaultFeatureConfiguration(
            ConfigService service,
            FrameworkLogger logger,
            FeatureConfigHandler.TypeMismatchPolicy mismatchPolicy,
            Map<String, Object> globalDefaults
    ) {
        super(service);
        this.logger = Objects.requireNonNull(logger, "logger");
        this.mismatchPolicy = Objects.requireNonNull(mismatchPolicy, "mismatchPolicy");
        initializeGlobalDefaults(Objects.requireNonNull(globalDefaults, "globalDefaults"),
                path -> logger.info("Added missing global configuration key '" + path + "'"));
    }

    @Override
    protected FeatureConfigHandler createFeatureConfig(String normalizedFeatureName) {
        return new FeatureConfigHandler(
                configService(),
                globals(),
                normalizedFeatureName,
                mismatchPolicy,
                logger::info,
                logger::error
        );
    }
}
