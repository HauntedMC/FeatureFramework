package com.example.lifecycle;

import nl.hauntedmc.featureframework.api.feature.FeatureDeclaration;
import nl.hauntedmc.featureframework.api.feature.FeatureScope;
import nl.hauntedmc.featureframework.config.ConfigReloadResult;
import nl.hauntedmc.featureframework.paper.host.PaperFeature;
import nl.hauntedmc.featureframework.paper.host.PaperFeatureContext;
import nl.hauntedmc.featureframework.paper.time.BukkitTime;
import nl.hauntedmc.featureframework.toolkit.io.config.ConfigMap;
import org.bukkit.plugin.Plugin;

@FeatureDeclaration(
        scope = FeatureScope.NETWORK,name = "RemoteSync", version = "1.0.0", enabledByDefault = true)
public final class RemoteSyncFeature extends PaperFeature<Plugin> {
    private ExampleRemoteClient client;

    public RemoteSyncFeature(PaperFeatureContext<Plugin> context) {
        super(context);
    }

    @Override
    public ConfigMap defaultConfig() {
        return new ConfigMap().put("refresh-seconds", 30L);
    }

    @Override
    public void initialize() {
        client = new ExampleRemoteClient();
        long seconds = config().get("refresh-seconds", Long.class, 30L);

        resources().tasks().scheduleRepeatingTask(
                () -> logger().info("Fetched " + client.fetchSnapshot()),
                BukkitTime.seconds(seconds)
        );
    }

    @Override
    public ConfigReloadResult applyConfiguration() {
        return ConfigReloadResult.RECREATE_REQUIRED;
    }

    @Override
    public void disable() {
        if (client != null) {
            client.close();
            client = null;
        }
    }
}
