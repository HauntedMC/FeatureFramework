package nl.hauntedmc.featureframework.paper.host;

import nl.hauntedmc.featureframework.api.feature.FeatureId;
import nl.hauntedmc.featureframework.api.service.CapabilityRegistry;
import nl.hauntedmc.featureframework.config.FeatureConfigHandler;
import nl.hauntedmc.featureframework.host.ManagedFeatureContext;
import nl.hauntedmc.featureframework.loader.ResolvedFeatureDefinition;
import nl.hauntedmc.featureframework.paper.lifecycle.PaperFeatureResources;
import nl.hauntedmc.featureframework.paper.localization.PaperLocalization;
import nl.hauntedmc.featureframework.paper.log.FeatureLogger;
import nl.hauntedmc.featureframework.service.InternalServiceRegistry;
import nl.hauntedmc.featureframework.toolkit.io.config.ConfigService;
import org.bukkit.plugin.Plugin;

import java.util.Objects;

/** Standard context received by features hosted in a Paper plugin. */
public final class PaperFeatureContext<P extends Plugin>
        extends ManagedFeatureContext<P, PaperFeatureResources, FeatureLogger, PaperLocalization> {
    private final ConfigService files;

    public PaperFeatureContext(
            P plugin,
            ResolvedFeatureDefinition<?, ?> definition,
            FeatureConfigHandler config,
            PaperLocalization localization,
            PaperFeatureResources resources,
            FeatureLogger logger,
            CapabilityRegistry capabilities,
            InternalServiceRegistry<FeatureId> internalServices,
            ConfigService files
    ) {
        super(plugin, definition, config, localization, resources, logger,
                capabilities, internalServices, resources.capabilities());
        this.files = Objects.requireNonNull(files, "files");
        definition.requiredResourceExtensions().forEach(type -> {
            if (!resources.extensions().contains(type)) {
                throw new IllegalStateException("Required resource extension is unavailable for "
                        + definition.featureName() + ": " + type.getName());
            }
        });
    }

    public ConfigService files() { return files; }
}
