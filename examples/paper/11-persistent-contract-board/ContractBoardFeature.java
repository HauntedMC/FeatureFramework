package com.example.contracts;

import nl.hauntedmc.dataprovider.database.DatabaseType;
import nl.hauntedmc.dataprovider.database.relational.RelationalDatabaseProvider;
import nl.hauntedmc.featureframework.api.feature.FeatureClassification;
import nl.hauntedmc.featureframework.api.feature.FeatureDeclaration;
import nl.hauntedmc.featureframework.api.feature.FeatureRole;
import nl.hauntedmc.featureframework.config.ConfigReloadResult;
import nl.hauntedmc.featureframework.integration.dataprovider.FeatureDataManager;
import nl.hauntedmc.featureframework.paper.host.PaperDataProviderFeature;
import nl.hauntedmc.featureframework.paper.host.PaperFeatureContext;
import nl.hauntedmc.featureframework.paper.time.BukkitTime;
import nl.hauntedmc.featureframework.toolkit.io.cache.CacheType;
import nl.hauntedmc.featureframework.toolkit.io.cache.FileCacheStore;
import nl.hauntedmc.featureframework.toolkit.io.config.ConfigMap;
import nl.hauntedmc.featureframework.toolkit.io.localization.MessageMap;

import java.time.Duration;

@FeatureDeclaration(
        name = "ContractBoard",
        version = "1.0.0",
        enabledByDefault = false,
        classification = FeatureClassification.CAPABILITY_PROVIDER,
        roles = FeatureRole.OPERATOR_FACING,
        providesCapabilities = ContractBoardApi.class,
        requiresPlugins = "DataProvider"
)
public final class ContractBoardFeature extends PaperDataProviderFeature<NetworkPlugin> {
    private ContractBoardService service;

    public ContractBoardFeature(PaperFeatureContext<NetworkPlugin, FeatureDataManager> context) {
        super(context);
    }

    @Override
    public ConfigMap getDefaultConfig() {
        return new ConfigMap()
                .put("database.connection", FeatureDataManager.SYSTEM_DATA_RW_CONNECTION)
                .put("cache.hot-ttl-seconds", 20L)
                .put("cache.snapshot-size", 100)
                .put("refresh.seconds", 30L)
                .put("contracts.max-reward", 50_000)
                .put("join-notice.minimum-open", 3);
    }

    @Override
    public MessageMap getDefaultMessages() {
        MessageMap messages = new MessageMap();
        messages.add("contracts.header", "<gold><bold>Open contracts</bold></gold> <gray>({count})</gray>");
        messages.add("contracts.entry", "<yellow>{reward}</yellow> <gray>—</gray> <white>{description}</white> <dark_gray>({id})</dark_gray>");
        messages.add("contracts.empty", "<gray>There are no open contracts.</gray>");
        messages.add("contracts.posted", "<green>Posted contract <white>{id}</white> for <gold>{reward}</gold>.</green>");
        messages.add("contracts.claimed", "<green>You claimed contract <white>{id}</white>.</green>");
        messages.add("contracts.claim-failed", "<red>That contract is no longer available.</red>");
        messages.add("contracts.invalid", "<red>{reason}</red>");
        messages.add("contracts.join-notice", "<gold>{count}</gold> <yellow>contracts are waiting. Use <white>/contracts</white>.</yellow>");
        return messages;
    }

    @Override
    public void initialize() {
        String connectionName = getConfigHandler().get(
                "database.connection", String.class, FeatureDataManager.SYSTEM_DATA_RW_CONNECTION);
        RelationalDatabaseProvider database = (RelationalDatabaseProvider) resources().getDataManager()
                .registerConnection("contracts-sql", DatabaseType.MYSQL, connectionName)
                .orElseThrow(() -> new IllegalStateException(
                        "ContractBoard requires MYSQL/" + connectionName));

        ContractRepository repository = new ContractRepository(database.getDataSource());
        repository.ensureSchema();

        FileCacheStore diskCache = (FileCacheStore) resources().getCacheManager()
                .getCacheDirectory(getFeatureName(), "snapshots")
                .getStore("contract-board", CacheType.JSON);
        ContractSnapshotCache cache = new ContractSnapshotCache(diskCache);

        int snapshotSize = getConfigHandler().get("cache.snapshot-size", Integer.class, 100);
        long ttlSeconds = getConfigHandler().get("cache.hot-ttl-seconds", Long.class, 20L);
        int maxReward = getConfigHandler().get("contracts.max-reward", Integer.class, 50_000);
        service = new ContractBoardService(
                this, repository, cache, Duration.ofSeconds(ttlSeconds), maxReward, snapshotSize);

        resources().getApiManager().registerService(ContractBoardApi.class, service);
        resources().getCommandManager().registerBrigadierCommand(new ContractCommand(this, service));
        resources().getListenerManager().registerListener(new ContractJoinListener(this, service));

        long refreshSeconds = getConfigHandler().get("refresh.seconds", Long.class, 30L);
        resources().getTaskManager().scheduleAsyncRepeatingTask(
                service::refreshSnapshot,
                BukkitTime.ticks(0),
                BukkitTime.seconds(refreshSeconds)
        );
        logger().info("ContractBoard ready; SQL, cache, command, listener, and refresh task share one lifetime");
    }

    @Override
    public ConfigReloadResult applyConfiguration() {
        // Connection, cache TTL, validation, and task cadence form one immutable runtime generation.
        return ConfigReloadResult.RECREATE_REQUIRED;
    }

    @Override
    public void disable() {
        if (service != null) {
            service.close();
            service = null;
        }
    }
}
