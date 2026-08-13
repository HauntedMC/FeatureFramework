package com.example.networkops.velocity;

import nl.hauntedmc.featureframework.api.feature.FeatureDeclaration;
import nl.hauntedmc.featureframework.config.ConfigReloadResult;
import nl.hauntedmc.featureframework.toolkit.io.config.ConfigMap;
import nl.hauntedmc.featureframework.velocity.host.VelocityFeature;
import nl.hauntedmc.featureframework.velocity.host.VelocityFeatureContext;

import java.time.Duration;

/** A simple operational target whose task must be recreated after its cadence changes. */
@FeatureDeclaration(name = "NetworkStatus", version = "1.0.0", enabledByDefault = true)
public final class NetworkStatusFeature extends VelocityFeature<ProxyPlugin, Void> {
    public NetworkStatusFeature(VelocityFeatureContext<ProxyPlugin, Void> context) {
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
                () -> logger().info("Network status heartbeat"), Duration.ofSeconds(seconds));
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
