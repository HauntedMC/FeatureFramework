package com.example.myplugin.welcome;

import nl.hauntedmc.featureframework.config.ConfigReloadResult;
import nl.hauntedmc.featureframework.paper.host.PaperFeature;
import nl.hauntedmc.featureframework.paper.host.PaperFeatureContext;
import nl.hauntedmc.featureframework.toolkit.io.config.ConfigMap;
import nl.hauntedmc.featureframework.toolkit.io.localization.MessageMap;
import org.bukkit.plugin.Plugin;

public final class ConfigurableWelcomeFeature extends PaperFeature<Plugin, Void> {
    public ConfigurableWelcomeFeature(PaperFeatureContext<Plugin, Void> context) {
        super(context);
    }

    @Override
    public ConfigMap getDefaultConfig() {
        return new ConfigMap()
                .put("enabled-on-join", true)
                .put("delay-ticks", 10L);
    }

    @Override
    public MessageMap getDefaultMessages() {
        MessageMap messages = new MessageMap();
        messages.add("welcome", "<green>Welcome to the server!</green>");
        return messages;
    }

    @Override
    public void initialize() {
        Boolean enabled = getConfigHandler().get("enabled-on-join", Boolean.class);
        logger().info("Join welcome enabled: " + enabled);
    }

    @Override
    public ConfigReloadResult applyConfiguration() {
        return ConfigReloadResult.RECREATE_REQUIRED;
    }

    @Override
    public void disable() {
    }
}
