package com.example.proxy.status;

import nl.hauntedmc.featureframework.config.ConfigReloadResult;
import nl.hauntedmc.featureframework.toolkit.io.config.ConfigMap;
import nl.hauntedmc.featureframework.toolkit.io.localization.MessageMap;
import nl.hauntedmc.featureframework.velocity.host.VelocityFeature;
import nl.hauntedmc.featureframework.velocity.host.VelocityFeatureContext;

public final class ConfigurableProxyFeature extends VelocityFeature<Object, Void> {
    public ConfigurableProxyFeature(VelocityFeatureContext<Object, Void> context) {
        super(context);
    }

    @Override
    public ConfigMap getDefaultConfig() {
        return new ConfigMap()
                .put("announce-switches", true)
                .put("sample-interval-seconds", 30);
    }

    @Override
    public MessageMap getDefaultMessages() {
        MessageMap messages = new MessageMap();
        messages.add("server-switch", "<gray>Connecting you to <white>{server}</white>...</gray>");
        return messages;
    }

    @Override
    public void initialize() {
        logger().info("Configurable proxy feature initialized");
    }

    @Override
    public ConfigReloadResult applyConfiguration() {
        return ConfigReloadResult.RECREATE_REQUIRED;
    }

    @Override
    public void disable() {
    }
}
