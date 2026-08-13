package com.example.paperdata;

import nl.hauntedmc.featureframework.api.feature.FeatureId;
import nl.hauntedmc.featureframework.config.DefaultFeatureConfiguration;
import nl.hauntedmc.featureframework.host.FeatureCollection;
import nl.hauntedmc.featureframework.host.FeatureDefinition;
import nl.hauntedmc.featureframework.paper.command.brigadier.BrigadierDispatcher;
import nl.hauntedmc.featureframework.paper.host.PaperFeature;
import nl.hauntedmc.featureframework.paper.host.PaperFeatureContext;
import nl.hauntedmc.featureframework.paper.host.PaperFeatureHostComposition;
import nl.hauntedmc.featureframework.paper.lifecycle.PaperFeatureResourcesFactory;
import nl.hauntedmc.featureframework.paper.localization.PaperLocalization;
import nl.hauntedmc.featureframework.runtime.FeatureRuntime;
import nl.hauntedmc.featureframework.service.DefaultCapabilityRegistry;
import nl.hauntedmc.featureframework.toolkit.io.config.ConfigService;
import nl.hauntedmc.featureframework.toolkit.io.localization.Language;
import nl.hauntedmc.featureframework.toolkit.log.FrameworkLogger;

public final class DataExampleHost {
    private DataExampleHost() {
    }

    public static PaperFeatureHostComposition<MyPlugin, String, PaperFeature<MyPlugin, Void>, Void> create(
            MyPlugin plugin
    ) {
        FrameworkLogger logger = FrameworkLogger.from(plugin.getLogger());
        DefaultCapabilityRegistry capabilities = new DefaultCapabilityRegistry(
                MyPlugin.class.getPackageName(), MyPlugin.class.getClassLoader());
        FeatureRuntime<FeatureId, DefaultCapabilityRegistry> runtime =
                new FeatureRuntime<>(plugin.getName(), capabilities);
        ConfigService configService = new ConfigService(
                plugin.getDataFolder().toPath(), logger, MyPlugin.class.getClassLoader());
        DefaultFeatureConfiguration configuration = new DefaultFeatureConfiguration(configService, logger);
        PaperLocalization localization = new PaperLocalization(plugin, configService, player -> Language.EN);

        BrigadierDispatcher dispatcher = new BrigadierDispatcher(plugin, logger);
        dispatcher.resolveDispatcher();
        PaperFeatureResourcesFactory<Void> resources = PaperFeatureResourcesFactory.withoutDataProvider(
                plugin, plugin.getDataFolder().toPath(), dispatcher, () -> true, logger);

        FeatureDefinition<PaperFeature<MyPlugin, Void>, PaperFeatureContext<MyPlugin, Void>> readyPlayer =
                FeatureDefinition.<PaperFeature<MyPlugin, Void>, PaperFeatureContext<MyPlugin, Void>>builder(
                                "ReadyPlayer", "1.0.0", ReadyPlayerFeature.class, ReadyPlayerFeature::new)
                        .requiresPlugins("DataRegistry")
                        .enabledByDefault()
                        .build();

        return PaperFeatureHostComposition.builder(
                        plugin,
                        plugin.getPluginMeta().getVersion(),
                        "exampledataregistry",
                        runtime,
                        configuration,
                        localization,
                        resources::create,
                        FeatureCollection.of(readyPlayer),
                        logger)
                .hostName(plugin.getName())
                .dataRegistryPlugin("DataRegistry")
                .build();
    }
}
