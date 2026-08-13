package com.example.registryplugin;

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
import org.bukkit.plugin.java.JavaPlugin;

public final class MyPlugin extends JavaPlugin {
    private PaperFeatureHostComposition<
            MyPlugin,
            String,
            PaperFeature<MyPlugin, Void>,
            Void> featureHost;

    @Override
    public void onEnable() {
        FrameworkLogger frameworkLogger = FrameworkLogger.from(getLogger());
        DefaultCapabilityRegistry capabilities = new DefaultCapabilityRegistry(
                getClass().getPackageName(), getClass().getClassLoader());
        FeatureRuntime<FeatureId, DefaultCapabilityRegistry> runtime =
                new FeatureRuntime<>(getName(), capabilities);

        ConfigService configService = new ConfigService(
                getDataFolder().toPath(), frameworkLogger, getClass().getClassLoader());
        DefaultFeatureConfiguration configuration =
                new DefaultFeatureConfiguration(configService, frameworkLogger);
        PaperLocalization localization =
                new PaperLocalization(this, configService, player -> Language.EN);

        BrigadierDispatcher dispatcher = new BrigadierDispatcher(this, frameworkLogger);
        dispatcher.resolveDispatcher();
        PaperFeatureResourcesFactory<Void> resources =
                PaperFeatureResourcesFactory.withoutDataProvider(
                        this,
                        getDataFolder().toPath(),
                        dispatcher,
                        () -> true,
                        frameworkLogger);

        FeatureDefinition<PaperFeature<MyPlugin, Void>, PaperFeatureContext<MyPlugin, Void>> identity =
                FeatureDefinition.<PaperFeature<MyPlugin, Void>, PaperFeatureContext<MyPlugin, Void>>builder(
                                "Identity",
                                "1.0.0",
                                IdentityFeature.class,
                                IdentityFeature::new)
                        .requiresPlugins("DataRegistry")
                        .enabledByDefault()
                        .build();

        featureHost = PaperFeatureHostComposition.builder(
                        this,
                        getPluginMeta().getVersion(),
                        "exampleregistry",
                        runtime,
                        configuration,
                        localization,
                        resources::create,
                        FeatureCollection.of(identity),
                        frameworkLogger)
                .hostName(getName())
                .dataRegistryPlugin("DataRegistry")
                .build();
        featureHost.start();
    }

    @Override
    public void onDisable() {
        if (featureHost != null) featureHost.stop();
    }
}
