package com.example.dataplugin;

import nl.hauntedmc.dataprovider.database.DatabaseType;
import nl.hauntedmc.featureframework.integration.dataprovider.FeatureDataManager;
import nl.hauntedmc.featureframework.paper.host.PaperFeature;
import nl.hauntedmc.featureframework.paper.host.PaperFeatureContext;

public final class PlayerStorageFeature extends PaperFeature<MyPlugin, FeatureDataManager> {
    public PlayerStorageFeature(PaperFeatureContext<MyPlugin, FeatureDataManager> context) {
        super(context);
    }

    @Override
    public void initialize() {
        resources().getDataManager()
                .registerConnection(
                        "players",
                        DatabaseType.MYSQL,
                        FeatureDataManager.PLAYER_DATA_RW_CONNECTION)
                .orElseThrow(() -> new IllegalStateException(
                        "Required player database connection is unavailable"));
    }

    @Override
    public void disable() {
        // The feature resource scope closes FeatureDataManager automatically.
    }
}
