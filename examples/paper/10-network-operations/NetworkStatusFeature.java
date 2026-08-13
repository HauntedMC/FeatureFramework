package com.example.networkops.paper;

import nl.hauntedmc.featureframework.api.feature.FeatureDeclaration;
import nl.hauntedmc.featureframework.config.ConfigReloadResult;
import nl.hauntedmc.featureframework.paper.host.PaperFeature;
import nl.hauntedmc.featureframework.paper.host.PaperFeatureContext;
import nl.hauntedmc.featureframework.paper.time.BukkitTime;
import nl.hauntedmc.featureframework.toolkit.io.config.ConfigMap;

/** A simple operational target whose task must be recreated after its cadence changes. */
@FeatureDeclaration(name = "NetworkStatus", version = "1.0.0", enabledByDefault = true)
public final class NetworkStatusFeature extends PaperFeature<MyPlugin, Void> {
    public NetworkStatusFeature(PaperFeatureContext<MyPlugin, Void> context) {
        super(context);
    }

    @Override
    public ConfigMap getDefaultConfig() {
        return new ConfigMap().put("heartbeat-seconds", 30L);
    }

    @Override
    public void initialize() {
        long seconds = getConfigHandler().get("heartbeat-seconds", Long.class, 30L);
        resources().getTaskManager().scheduleRepeatingTask(
                () -> logger().info("Network status heartbeat"), BukkitTime.seconds(seconds));
    }

    @Override
    public ConfigReloadResult applyConfiguration() {
        return ConfigReloadResult.RECREATE_REQUIRED;
    }

    @Override
    public void disable() {
        // The task is owned by the feature resource scope and is cancelled automatically.
    }
}
