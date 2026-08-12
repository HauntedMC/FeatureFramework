package nl.hauntedmc.featureframework.paper.host;

import nl.hauntedmc.featureframework.api.feature.FeatureId;
import nl.hauntedmc.featureframework.api.service.CapabilityRegistry;
import nl.hauntedmc.featureframework.config.FeatureConfigHandler;
import nl.hauntedmc.featureframework.host.ManagedFeatureContext;
import nl.hauntedmc.featureframework.loader.FeatureDescriptor;
import nl.hauntedmc.featureframework.paper.lifecycle.PaperFeatureResources;
import nl.hauntedmc.featureframework.paper.localization.PaperLocalization;
import nl.hauntedmc.featureframework.paper.log.FeatureLogger;
import nl.hauntedmc.featureframework.service.InternalServiceRegistry;
import org.bukkit.plugin.Plugin;

import java.util.Objects;
import java.util.function.Supplier;

/** Standard context received by features hosted in a Paper plugin. */
public final class PaperFeatureContext<P extends Plugin, D>
        extends ManagedFeatureContext<P, PaperFeatureResources<D>, FeatureLogger, PaperLocalization> {
    private final Supplier<?> dataRegistry;

    public PaperFeatureContext(
            P plugin,
            FeatureDescriptor<?, ?> descriptor,
            FeatureConfigHandler config,
            PaperLocalization localization,
            PaperFeatureResources<D> resources,
            FeatureLogger logger,
            CapabilityRegistry capabilities,
            InternalServiceRegistry<FeatureId> internalServices
    ) {
        this(plugin, descriptor, config, localization, resources, logger, capabilities,
                internalServices, unavailableDataRegistry());
    }

    public PaperFeatureContext(
            P plugin,
            FeatureDescriptor<?, ?> descriptor,
            FeatureConfigHandler config,
            PaperLocalization localization,
            PaperFeatureResources<D> resources,
            FeatureLogger logger,
            CapabilityRegistry capabilities,
            InternalServiceRegistry<FeatureId> internalServices,
            Supplier<?> dataRegistry
    ) {
        super(plugin, descriptor, config, localization, resources, logger,
                capabilities, internalServices, resources.getApiManager());
        this.dataRegistry = Objects.requireNonNull(dataRegistry, "dataRegistry");
    }

    Object dataRegistryService() {
        return Objects.requireNonNull(dataRegistry.get(), "dataRegistry returned null");
    }

    private static Supplier<?> unavailableDataRegistry() {
        return () -> {
            throw new IllegalStateException("No DataRegistry supplier was configured for this Paper host");
        };
    }
}
