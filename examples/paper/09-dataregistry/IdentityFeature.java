package com.example.registryplugin;

import nl.hauntedmc.featureframework.api.feature.FeatureDeclaration;
import nl.hauntedmc.featureframework.api.feature.FeatureScope;
import nl.hauntedmc.dataregistry.api.DataRegistryApi;
import nl.hauntedmc.featureframework.integration.dataregistry.DataRegistryResources;
import nl.hauntedmc.featureframework.paper.host.PaperFeature;
import nl.hauntedmc.featureframework.paper.host.PaperFeatureContext;
import nl.hauntedmc.featureframework.paper.integration.dataregistry.PaperDataRegistryIdentityGate;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

@FeatureDeclaration(
        scope = FeatureScope.NODE,
        name = "Identity", version = "1.0.0", enabledByDefault = true,
        requiresPlugins = "DataRegistry", requiresResourceExtensions = DataRegistryResources.class)
public final class IdentityFeature extends PaperFeature<MyPlugin>
        implements PaperDataRegistryIdentityGate.Context {
    public IdentityFeature(PaperFeatureContext<MyPlugin> context) {
        super(context);
    }

    @Override
    public void initialize() {
        resources().listeners().registerListener(new JoinListener());
    }

    @Override
    public void disable() {
    }

    @Override public DataRegistryApi dataRegistry() {
        return resources().extensions().require(DataRegistryResources.KEY).registry();
    }
    @Override public void scheduleContinuation(Runnable continuation) {
        resources().tasks().scheduleOneTimeTask(continuation);
    }
    @Override public boolean hostAvailable() { return plugin().isEnabled(); }
    @Override public void warn(String message) { logger().warning(message); }

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
