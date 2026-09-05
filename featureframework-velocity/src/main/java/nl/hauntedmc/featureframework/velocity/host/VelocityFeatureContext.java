package nl.hauntedmc.featureframework.velocity.host;

import com.velocitypowered.api.proxy.ProxyServer;
import nl.hauntedmc.featureframework.api.feature.FeatureId;
import nl.hauntedmc.featureframework.api.service.CapabilityRegistry;
import nl.hauntedmc.featureframework.config.FeatureConfigHandler;
import nl.hauntedmc.featureframework.host.ManagedFeatureContext;
import nl.hauntedmc.featureframework.loader.ResolvedFeatureDefinition;
import nl.hauntedmc.featureframework.service.InternalServiceRegistry;
import nl.hauntedmc.featureframework.toolkit.io.config.ConfigService;
import nl.hauntedmc.featureframework.velocity.lifecycle.VelocityFeatureResources;
import nl.hauntedmc.featureframework.velocity.localization.VelocityLocalization;
import nl.hauntedmc.featureframework.velocity.log.FeatureLogger;

import java.util.Objects;

/** Standard context received by features hosted in a Velocity plugin. */
public final class VelocityFeatureContext<P>
        extends ManagedFeatureContext<P, VelocityFeatureResources, FeatureLogger, VelocityLocalization> {
    private final ProxyServer proxy;
    private final ConfigService files;

    public VelocityFeatureContext(
            P plugin,
            ResolvedFeatureDefinition<?, ?> definition,
            FeatureConfigHandler config,
            VelocityLocalization localization,
            VelocityFeatureResources resources,
            FeatureLogger logger,
            CapabilityRegistry capabilities,
            InternalServiceRegistry<FeatureId> internalServices,
            ProxyServer proxy,
            ConfigService files
    ) {
        super(plugin, definition, config, localization, resources, logger,
                capabilities, internalServices, resources.serviceManager(), resources.ownership());
        this.proxy = Objects.requireNonNull(proxy, "proxy");
        this.files = Objects.requireNonNull(files, "files");
        definition.requiredResourceExtensions().forEach(type -> {
            if (!resources.extensions().contains(type)) {
                throw new IllegalStateException("Required resource extension is unavailable for "
                        + definition.featureName() + ": " + type.getName());
            }
        });
    }

    public ProxyServer proxy() { return proxy; }
    public ConfigService files() { return files; }
}
