package com.example.registryplugin;

import nl.hauntedmc.featureframework.paper.host.PaperDataRegistryFeature;
import nl.hauntedmc.featureframework.paper.host.PaperFeatureContext;
import nl.hauntedmc.featureframework.paper.integration.dataregistry.PaperDataRegistryIdentityGate;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public final class IdentityFeature extends PaperDataRegistryFeature<MyPlugin, Void> {
    public IdentityFeature(PaperFeatureContext<MyPlugin, Void> context) {
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
                    IdentityFeature.this,
                    event.getPlayer(),
                    (player, identity) -> logger().info(
                            player.getName() + " has DataRegistry player id " + identity.playerId()),
                    "load player identity"
            );
        }
    }
}
