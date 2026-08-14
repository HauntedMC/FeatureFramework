package nl.hauntedmc.featureframework.paper.lifecycle;

import nl.hauntedmc.featureframework.api.feature.FeatureId;
import nl.hauntedmc.featureframework.command.CommandLabelOwnership;
import nl.hauntedmc.featureframework.lifecycle.FeatureResourceFactoryCore;
import nl.hauntedmc.featureframework.loader.ResolvedFeatureDefinition;
import nl.hauntedmc.featureframework.paper.command.FeatureCommandManager;
import nl.hauntedmc.featureframework.paper.command.brigadier.BrigadierDispatcher;
import nl.hauntedmc.featureframework.resource.FeatureResourceContributor;
import nl.hauntedmc.featureframework.resource.FeatureResourceContributionPipeline;
import nl.hauntedmc.featureframework.resource.FeatureResourceRequest;
import nl.hauntedmc.featureframework.service.DefaultCapabilityRegistry;
import nl.hauntedmc.featureframework.service.InternalServiceRegistry;
import nl.hauntedmc.featureframework.toolkit.log.FrameworkLogger;
import org.bukkit.plugin.Plugin;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/** Assembles dependency-clean Paper resources and applies optional contributors. */
public final class PaperFeatureResourcesFactory {
    private final Plugin plugin;
    private final BrigadierDispatcher dispatcher;
    private final BooleanSupplier overwriteConflicts;
    private final FrameworkLogger logger;
    private final FeatureResourceFactoryCore core;
    private final List<FeatureResourceContributor<PaperFeatureResources>> contributors;
    private final CommandLabelOwnership commandOwnership = new CommandLabelOwnership();

    public PaperFeatureResourcesFactory(
            Plugin plugin,
            Path dataDirectory,
            BrigadierDispatcher dispatcher,
            BooleanSupplier overwriteConflicts,
            FrameworkLogger logger,
            List<? extends FeatureResourceContributor<PaperFeatureResources>> contributors
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
        this.overwriteConflicts = Objects.requireNonNull(overwriteConflicts, "overwriteConflicts");
        this.logger = Objects.requireNonNull(logger, "logger");
        core = new FeatureResourceFactoryCore(dataDirectory, logger);
        this.contributors = List.copyOf(Objects.requireNonNull(contributors, "contributors"));
    }

    public PaperFeatureResources create(
            ResolvedFeatureDefinition<?, ?> definition,
            DefaultCapabilityRegistry capabilities,
            InternalServiceRegistry<FeatureId> internalServices
    ) {
        FeatureResourceRequest request = FeatureResourceRequest.from(definition);
        FeatureResourceFactoryCore.Bundle common = core.create(request.id().value(), capabilities, internalServices);
        PaperFeatureResources resources = new PaperFeatureResources(
                new FeatureTaskManager(plugin),
                new FeatureCommandManager(plugin, dispatcher, commandOwnership, overwriteConflicts, logger),
                new FeatureListenerManager(plugin),
                common.cacheManager(),
                common.serviceManager(),
                common.ownership(),
                common.extensions()
        );
        return FeatureResourceContributionPipeline.apply(request, contributors, resources, common.extensions());
    }
}
