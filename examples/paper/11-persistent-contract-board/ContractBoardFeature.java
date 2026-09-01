package com.example.contracts;

import nl.hauntedmc.dataprovider.database.DatabaseType;
import nl.hauntedmc.dataprovider.database.relational.RelationalDatabaseProvider;
import nl.hauntedmc.featureframework.api.feature.FeatureDeclaration;
import nl.hauntedmc.featureframework.api.feature.FeatureScope;
import nl.hauntedmc.featureframework.api.feature.FeatureRole;
import nl.hauntedmc.featureframework.config.ConfigReloadResult;
import nl.hauntedmc.featureframework.integration.dataprovider.DataProviderResources;
import nl.hauntedmc.featureframework.paper.host.PaperFeature;
import nl.hauntedmc.featureframework.paper.host.PaperFeatureContext;
import nl.hauntedmc.featureframework.paper.time.BukkitTime;
import nl.hauntedmc.featureframework.toolkit.io.cache.CacheType;
import nl.hauntedmc.featureframework.toolkit.io.cache.FileCacheStore;
import nl.hauntedmc.featureframework.toolkit.io.config.ConfigMap;
import nl.hauntedmc.featureframework.toolkit.io.localization.MessageMap;

import java.time.Duration;

@FeatureDeclaration(
        scope = FeatureScope.NODE,
        name = "ContractBoard",
        version = "1.0.0",
        enabledByDefault = false,
        roles = FeatureRole.OPERATOR_FACING,
        providesCapabilities = ContractBoardApi.class,
        requiresPlugins = "DataProvider",
        requiresResourceExtensions = DataProviderResources.class
)
public final class ContractBoardFeature extends PaperFeature<NetworkPlugin> {
    private ContractBoardService service;

    public ContractBoardFeature(PaperFeatureContext<NetworkPlugin> context) {
        super(context);
    }

    @Override
    public ConfigMap defaultConfig() {
        return new ConfigMap()
                .put("database.connection", DataProviderResources.SYSTEM_DATA_RW_CONNECTION)
                .put("cache.hot-ttl-seconds", 20L)
                .put("cache.snapshot-size", 100)
                .put("refresh.seconds", 30L)
                .put("contracts.max-reward", 50_000)
                .put("join-notice.minimum-open", 3);
    }

    @Override
    public MessageMap defaultMessages() {
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
        String connectionName = config().get(
                "database.connection", String.class, DataProviderResources.SYSTEM_DATA_RW_CONNECTION);
        RelationalDatabaseProvider database = (RelationalDatabaseProvider) resources().extensions()
                .require(DataProviderResources.KEY)
                .registerConnection("contracts-sql", DatabaseType.MYSQL, connectionName)
                .orElseThrow(() -> new IllegalStateException(
                        "ContractBoard requires MYSQL/" + connectionName));

        ContractRepository repository = new ContractRepository(database.getDataSource());
        repository.ensureSchema();

        FileCacheStore diskCache = (FileCacheStore) resources().caches()
                .getCacheDirectory(name(), "snapshots")
                .getStore("contract-board", CacheType.JSON);
        ContractSnapshotCache cache = new ContractSnapshotCache(diskCache);

        int snapshotSize = config().get("cache.snapshot-size", Integer.class, 100);
        long ttlSeconds = config().get("cache.hot-ttl-seconds", Long.class, 20L);
        int maxReward = config().get("contracts.max-reward", Integer.class, 50_000);
        service = new ContractBoardService(
                this, repository, cache, Duration.ofSeconds(ttlSeconds), maxReward, snapshotSize);

        resources().capabilities().registerService(ContractBoardApi.class, service);
        resources().commands().registerBrigadierCommand(new ContractCommand(this, service));
        resources().listeners().registerListener(new ContractJoinListener(this, service));

        long refreshSeconds = config().get("refresh.seconds", Long.class, 30L);
        resources().tasks().scheduleAsyncRepeatingTask(
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
