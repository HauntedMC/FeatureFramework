package com.example.rollouts;

import nl.hauntedmc.dataprovider.database.messaging.MessagingDataAccess;
import nl.hauntedmc.dataprovider.database.messaging.api.Subscription;
import nl.hauntedmc.featureframework.api.feature.FeatureDeclaration;
import nl.hauntedmc.featureframework.api.feature.FeatureScope;
import nl.hauntedmc.featureframework.api.feature.FeatureRole;
import nl.hauntedmc.featureframework.config.ConfigReloadResult;
import nl.hauntedmc.featureframework.integration.dataprovider.DataProviderResources;
import nl.hauntedmc.featureframework.toolkit.io.cache.CacheType;
import nl.hauntedmc.featureframework.toolkit.io.cache.FileCacheStore;
import nl.hauntedmc.featureframework.toolkit.io.config.ConfigMap;
import nl.hauntedmc.featureframework.toolkit.io.localization.MessageMap;
import nl.hauntedmc.featureframework.velocity.host.VelocityFeature;
import nl.hauntedmc.featureframework.velocity.host.VelocityFeatureContext;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@FeatureDeclaration(
        scope = FeatureScope.NODE,
        name = "AdaptiveRollout",
        version = "1.0.0",
        enabledByDefault = false,
        roles = FeatureRole.OPERATOR_FACING,
        providesCapabilities = RolloutRoutingApi.class,
        requiresPlugins = "dataprovider",
        requiresResourceExtensions = DataProviderResources.class
)
public final class AdaptiveRolloutFeature extends VelocityFeature<RolloutProxyPlugin> {
    private Subscription healthSubscription;

    public AdaptiveRolloutFeature(
            VelocityFeatureContext<RolloutProxyPlugin> context
    ) {
        super(context);
    }

    @Override
    public ConfigMap defaultConfig() {
        return new ConfigMap()
                .put("messaging.connection", DataProviderResources.DEFAULT_REDIS_MESSAGING_CONNECTION)
                .put("messaging.channel", "deployments.backend-health")
                .put("health.stale-after-seconds", 15L)
                .put("routing.stable-server", "survival-stable")
                .put("routing.canary-server", "survival-canary")
                .put("routing.fallback-server", "lobby")
                .put("routing.canary-percent", 10);
    }

    @Override
    public MessageMap defaultMessages() {
        MessageMap messages = new MessageMap();
        messages.add("rollout.rerouted", "<yellow>Routing you to <white>{server}</white> <gray>({reason})</gray>.</yellow>");
        messages.add("rollout.unavailable", "<red>No healthy server is currently available for this game mode.</red>");
        messages.add("rollout.misconfigured", "<red>Rollout target <white>{server}</white> is not registered on this proxy.</red>");
        messages.add("rollout.status-header", "<gold><bold>Backend health</bold></gold> <gray>({count} snapshots)</gray>");
        messages.add("rollout.status-entry", "<white>{server}</white><gray>: healthy={healthy}, online={online}, age={age}s</gray>");
        return messages;
    }

    @Override
    public void initialize() {
        long staleSeconds = config().get("health.stale-after-seconds", Long.class, 15L);
        if (staleSeconds < 2L) {
            throw new IllegalArgumentException("health.stale-after-seconds must be at least 2");
        }
        Duration staleAfter = Duration.ofSeconds(staleSeconds);
        FileCacheStore disk = (FileCacheStore) resources().caches()
                .getCacheDirectory(name(), "backend-health")
                .getStore("last-known", CacheType.JSON);
        BackendHealthStore health = new BackendHealthStore(disk, staleAfter);
        RolloutPolicy policy = new RolloutPolicy(
                health,
                text("routing.stable-server"),
                text("routing.canary-server"),
                text("routing.fallback-server"),
                config().get("routing.canary-percent", Integer.class, 10)
        );

        String connection = config().get(
                "messaging.connection", String.class,
                DataProviderResources.DEFAULT_REDIS_MESSAGING_CONNECTION);
        MessagingDataAccess bus = resources().extensions().require(DataProviderResources.KEY)
                .registerRedisMessagingDataAccess("rollout-health", connection)
                .orElseThrow(() -> new IllegalStateException(
                        "AdaptiveRollout requires Redis messaging connection '" + connection + "'"));
        String channel = text("messaging.channel");
        healthSubscription = bus.subscribe(
                channel,
                BackendHealthMessage.TYPE,
                BackendHealthMessage.class,
                health::ingest
        );

        resources().capabilities().registerService(RolloutRoutingApi.class, policy);
        resources().listeners().registerListener(new RolloutListener(this, policy));
        resources().commands().registerBrigadierCommand(new RolloutCommand(this, policy));
        resources().tasks().scheduleRepeatingTask(
                health::removeExpired,
                staleAfter.multipliedBy(2)
        );
        logger().info("AdaptiveRollout subscribed to '" + channel + "'; routing is cache-only on event paths");
    }

    @Override
    public ConfigReloadResult applyConfiguration() {
        // Subscription, stale policy, targets, cohort size, and cache are replaced together.
        return ConfigReloadResult.RECREATE_REQUIRED;
    }

    @Override
    public void disable() {
        Subscription current = healthSubscription;
        healthSubscription = null;
        if (current == null) return;
        try {
            current.unsubscribe().get(5, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            logger().warn("Interrupted while closing the backend-health subscription");
        } catch (Exception failure) {
            logger().warn("Could not confirm backend-health unsubscription: " + failure.getMessage());
        }
    }

    private String text(String key) {
        String value = config().get(key, String.class);
        if (value == null || value.isBlank()) throw new IllegalArgumentException(key + " must not be blank");
        return value.trim();
    }
}
