package nl.hauntedmc.featureframework.velocity.host;

import com.velocitypowered.api.proxy.ProxyServer;
import nl.hauntedmc.featureframework.api.feature.FeatureId;
import nl.hauntedmc.featureframework.api.service.CapabilityRegistry;
import nl.hauntedmc.featureframework.config.FeatureConfigHandler;
import nl.hauntedmc.featureframework.host.ManagedFeatureContext;
import nl.hauntedmc.featureframework.loader.FeatureDescriptor;
import nl.hauntedmc.featureframework.service.InternalServiceRegistry;
import nl.hauntedmc.featureframework.velocity.lifecycle.VelocityFeatureResources;
import nl.hauntedmc.featureframework.velocity.localization.VelocityLocalization;
import nl.hauntedmc.featureframework.velocity.log.FeatureLogger;

import java.util.Objects;
import java.util.function.Supplier;

/** Standard context received by features hosted in a Velocity plugin. */
public final class VelocityFeatureContext<P, D>
        extends ManagedFeatureContext<P, VelocityFeatureResources<D>, FeatureLogger, VelocityLocalization> {
    private final ProxyServer proxy;
    private final Supplier<?> dataRegistry;

    public VelocityFeatureContext(
            P plugin,
            FeatureDescriptor<?, ?> descriptor,
            FeatureConfigHandler config,
            VelocityLocalization localization,
            VelocityFeatureResources<D> resources,
            FeatureLogger logger,
            CapabilityRegistry capabilities,
            InternalServiceRegistry<FeatureId> internalServices
    ) {
        this(plugin, descriptor, config, localization, resources, logger, capabilities,
                internalServices, null, unavailableDataRegistry());
    }

    public VelocityFeatureContext(
            P plugin,
            FeatureDescriptor<?, ?> descriptor,
            FeatureConfigHandler config,
            VelocityLocalization localization,
            VelocityFeatureResources<D> resources,
            FeatureLogger logger,
            CapabilityRegistry capabilities,
            InternalServiceRegistry<FeatureId> internalServices,
            ProxyServer proxy,
            Supplier<?> dataRegistry
    ) {
        super(plugin, descriptor, config, localization, resources, logger,
                capabilities, internalServices, resources.getApiManager());
        this.proxy = proxy;
        this.dataRegistry = Objects.requireNonNull(dataRegistry, "dataRegistry");
    }

    public ProxyServer proxy() {
        return Objects.requireNonNull(proxy, "No ProxyServer was configured for this Velocity host");
    }

    Object dataRegistryService() {
        return Objects.requireNonNull(dataRegistry.get(), "dataRegistry returned null");
    }

    private static Supplier<?> unavailableDataRegistry() {
        return () -> {
            throw new IllegalStateException("No DataRegistry supplier was configured for this Velocity host");
        };
    }
}
