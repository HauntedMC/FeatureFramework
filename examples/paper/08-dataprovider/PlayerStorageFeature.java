package com.example.dataplugin;

import nl.hauntedmc.featureframework.api.feature.FeatureDeclaration;
import nl.hauntedmc.dataprovider.database.DatabaseType;
import nl.hauntedmc.featureframework.integration.dataprovider.DataProviderResources;
import nl.hauntedmc.featureframework.integration.dataprovider.DataProviderConnections;
import nl.hauntedmc.featureframework.paper.host.PaperFeature;
import nl.hauntedmc.featureframework.paper.host.PaperFeatureContext;

@FeatureDeclaration(
        name = "PlayerStorage", version = "1.0.0", enabledByDefault = true,
        requiresPlugins = "DataProvider", requiresResourceExtensions = DataProviderResources.class)
public final class PlayerStorageFeature extends PaperFeature<MyPlugin> {
    public PlayerStorageFeature(PaperFeatureContext<MyPlugin> context) {
        super(context);
    }

    @Override
    public void initialize() {
        DataProviderResources data = resources().extensions().require(DataProviderResources.KEY);

        data.registerConnection(
                        "players",
                        DatabaseType.MYSQL,
                        DataProviderConnections.PLAYER_DATA_RW)
                .orElseThrow(() -> new IllegalStateException(
                        "Required player database connection is unavailable"));

        logger().info("PlayerStorage data resources are ready");
    }

    @Override
    public void disable() {
        // DataProviderResources is owned by PaperFeatureResources and is closed automatically.
    }
}
