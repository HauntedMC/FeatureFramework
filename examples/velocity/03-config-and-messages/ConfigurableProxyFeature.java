package com.example.proxy.status;

import nl.hauntedmc.featureframework.api.feature.FeatureDeclaration;
import nl.hauntedmc.featureframework.config.ConfigReloadResult;
import nl.hauntedmc.featureframework.toolkit.io.config.ConfigMap;
import nl.hauntedmc.featureframework.toolkit.io.localization.MessageMap;
import nl.hauntedmc.featureframework.velocity.host.VelocityFeature;
import nl.hauntedmc.featureframework.velocity.host.VelocityFeatureContext;

@FeatureDeclaration(name = "Status", version = "1.0.0", enabledByDefault = true)
public final class ConfigurableProxyFeature extends VelocityFeature<Object> {
    public ConfigurableProxyFeature(VelocityFeatureContext<Object> context) {
        super(context);
    }

    @Override
    public ConfigMap defaultConfig() {
        return new ConfigMap()
                .put("announce-switches", true)
                .put("sample-interval-seconds", 30);
    }

    @Override
    public MessageMap defaultMessages() {
        MessageMap messages = new MessageMap();
        messages.add("server-switch", "<gray>Connecting you to <white>{server}</white>...</gray>");
        return messages;
    }

    @Override
    public void initialize() {
        Boolean announceSwitches = config().get("announce-switches", Boolean.class);
        logger().info("Announce server switches: " + announceSwitches);
    }

    @Override
    public ConfigReloadResult applyConfiguration() {
        return ConfigReloadResult.RECREATE_REQUIRED;
    }

    @Override
    public void disable() {
    }
}
