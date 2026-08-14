package nl.hauntedmc.featureframework.velocity.lifecycle;

import com.velocitypowered.api.proxy.ProxyServer;
import nl.hauntedmc.featureframework.api.feature.FeatureId;
import nl.hauntedmc.featureframework.command.CommandLabelOwnership;
import nl.hauntedmc.featureframework.lifecycle.FeatureResourceFactoryCore;
import nl.hauntedmc.featureframework.loader.ResolvedFeatureDefinition;
import nl.hauntedmc.featureframework.resource.FeatureResourceContributor;
import nl.hauntedmc.featureframework.resource.FeatureResourceContributionPipeline;
import nl.hauntedmc.featureframework.resource.FeatureResourceRequest;
import nl.hauntedmc.featureframework.service.DefaultCapabilityRegistry;
import nl.hauntedmc.featureframework.service.InternalServiceRegistry;
import nl.hauntedmc.featureframework.toolkit.log.FrameworkLogger;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** Assembles dependency-clean Velocity resources and applies optional contributors. */
public final class VelocityFeatureResourcesFactory {
    private final Object plugin;
    private final ProxyServer proxy;
    private final Logger platformLogger;
    private final FeatureResourceFactoryCore core;
    private final List<FeatureResourceContributor<VelocityFeatureResources>> contributors;
    private final CommandLabelOwnership commandOwnership = new CommandLabelOwnership();

    public VelocityFeatureResourcesFactory(
            Object plugin,
            ProxyServer proxy,
            Logger platformLogger,
            Path dataDirectory,
            FrameworkLogger logger,
            List<? extends FeatureResourceContributor<VelocityFeatureResources>> contributors
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.proxy = Objects.requireNonNull(proxy, "proxy");
        this.platformLogger = Objects.requireNonNull(platformLogger, "platformLogger");
        core = new FeatureResourceFactoryCore(dataDirectory, logger);
        this.contributors = List.copyOf(Objects.requireNonNull(contributors, "contributors"));
    }

    public VelocityFeatureResources create(
            ResolvedFeatureDefinition<?, ?> definition,
            DefaultCapabilityRegistry capabilities,
            InternalServiceRegistry<FeatureId> internalServices
    ) {
        FeatureResourceRequest request = FeatureResourceRequest.from(definition);
        FeatureResourceFactoryCore.Bundle common = core.create(request.id().value(), capabilities, internalServices);
        VelocityFeatureResources resources = new VelocityFeatureResources(
                new FeatureTaskManager(proxy.getScheduler(), plugin),
                new FeatureCommandManager(
                        plugin, proxy.getCommandManager(), commandOwnership, platformLogger, common.featureName()),
                new FeatureListenerManager(plugin, proxy.getEventManager()),
                common.cacheManager(),
                common.serviceManager(),
                common.ownership(),
                common.extensions()
        );
        return FeatureResourceContributionPipeline.apply(request, contributors, resources, common.extensions());
    }

    public CommandLabelOwnership commandOwnership() { return commandOwnership; }
}
