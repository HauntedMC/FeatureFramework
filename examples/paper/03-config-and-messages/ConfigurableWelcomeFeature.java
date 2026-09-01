package com.example.myplugin.welcome;

import nl.hauntedmc.featureframework.api.feature.FeatureDeclaration;
import nl.hauntedmc.featureframework.api.feature.FeatureScope;
import nl.hauntedmc.featureframework.config.ConfigReloadResult;
import nl.hauntedmc.featureframework.paper.host.PaperFeature;
import nl.hauntedmc.featureframework.paper.host.PaperFeatureContext;
import nl.hauntedmc.featureframework.toolkit.io.config.ConfigMap;
import nl.hauntedmc.featureframework.toolkit.io.localization.MessageMap;
import org.bukkit.plugin.Plugin;

@FeatureDeclaration(
        scope = FeatureScope.NODE,name = "Welcome", version = "1.0.0", enabledByDefault = true)
public final class ConfigurableWelcomeFeature extends PaperFeature<Plugin> {
    public ConfigurableWelcomeFeature(PaperFeatureContext<Plugin> context) {
        super(context);
    }

    @Override
    public ConfigMap defaultConfig() {
        return new ConfigMap()
                .put("enabled-on-join", true)
                .put("delay-ticks", 10L);
    }

    @Override
    public MessageMap defaultMessages() {
        MessageMap messages = new MessageMap();
        messages.add("welcome", "<green>Welcome to the server!</green>");
        return messages;
    }

    @Override
    public void initialize() {
        Boolean enabled = config().get("enabled-on-join", Boolean.class);
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
