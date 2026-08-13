package com.example.paperdata;

import nl.hauntedmc.featureframework.paper.host.PaperDataRegistryFeature;
import nl.hauntedmc.featureframework.paper.host.PaperFeatureContext;
import nl.hauntedmc.featureframework.paper.integration.dataregistry.PaperDataRegistryIdentityGate;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public final class ReadyPlayerFeature extends PaperDataRegistryFeature<MyPlugin, Void> {
    public ReadyPlayerFeature(PaperFeatureContext<MyPlugin, Void> context) {
        super(context);
    }

    @Override
    public void initialize() {
        resources().getListenerManager().registerListener(new JoinListener());
    }

    @Override
    public void disable() {
    }

    private final class JoinListener implements Listener {
        @EventHandler
        public void onJoin(PlayerJoinEvent event) {
            PaperDataRegistryIdentityGate.runWhenReady(
                    ReadyPlayerFeature.this,
                    event.getPlayer(),
                    player -> logger().info("DataRegistry is ready for " + player.getName()),
                    "prepare player data");
        }
    }
}
