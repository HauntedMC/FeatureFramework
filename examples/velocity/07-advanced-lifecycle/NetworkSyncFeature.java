package com.example.proxy.lifecycle;

import nl.hauntedmc.featureframework.config.ConfigReloadResult;
import nl.hauntedmc.featureframework.toolkit.io.config.ConfigMap;
import nl.hauntedmc.featureframework.velocity.host.VelocityFeature;
import nl.hauntedmc.featureframework.velocity.host.VelocityFeatureContext;

import java.time.Duration;

public final class NetworkSyncFeature extends VelocityFeature<Object, Void> {
    private ExampleNetworkClient client;

    public NetworkSyncFeature(VelocityFeatureContext<Object, Void> context) { super(context); }

    @Override
    public ConfigMap getDefaultConfig() {
        return new ConfigMap().put("poll-seconds", 15L);
    }

    @Override
    public void initialize() {
        client = new ExampleNetworkClient();
        long seconds = getConfigHandler().get("poll-seconds", Long.class, 15L);
        resources().getTaskManager().scheduleRepeatingTask(
                () -> logger().info("Polled " + client.poll()),
                Duration.ofSeconds(seconds)
        );
    }

    @Override public ConfigReloadResult applyConfiguration() { return ConfigReloadResult.RECREATE_REQUIRED; }

    @Override
    public void disable() {
        if (client != null) {
            client.close();
            client = null;
        }
    }
}
