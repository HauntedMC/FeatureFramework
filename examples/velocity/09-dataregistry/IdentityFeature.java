package com.example.registryproxy;

import nl.hauntedmc.featureframework.api.feature.FeatureDeclaration;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.proxy.Player;
import nl.hauntedmc.dataregistry.api.DataRegistryApi;
import nl.hauntedmc.featureframework.integration.dataregistry.DataRegistryResources;
import nl.hauntedmc.featureframework.velocity.host.VelocityFeature;
import nl.hauntedmc.featureframework.velocity.host.VelocityFeatureContext;
import nl.hauntedmc.featureframework.velocity.integration.dataregistry.VelocityDataRegistryIdentityGate;

import java.util.Optional;
import java.util.UUID;

@FeatureDeclaration(
        name = "Identity", version = "1.0.0", enabledByDefault = true,
        requiresPlugins = "dataregistry", requiresResourceExtensions = DataRegistryResources.class)
public final class IdentityFeature extends VelocityFeature<ProxyPlugin>
        implements VelocityDataRegistryIdentityGate.Context {
    public IdentityFeature(VelocityFeatureContext<ProxyPlugin> context) {
        super(context);
    }

    @Override
    public void initialize() {
        resources().listeners().registerListener(new LoginListener());
    }

    @Override
    public void disable() {
    }

    @Override public DataRegistryApi dataRegistry() {
        return resources().extensions().require(DataRegistryResources.KEY).registry();
    }
    @Override public void scheduleContinuation(Runnable continuation) {
        resources().tasks().scheduleTask(continuation);
    }
    @Override public Optional<Player> connectedPlayer(UUID playerId) {
        return context().proxy().getPlayer(playerId);
    }
    @Override public void warn(String message) { logger().warn(message); }

    private final class LoginListener {
        @Subscribe
        public void onPostLogin(PostLoginEvent event) {
            VelocityDataRegistryIdentityGate.runWhenReady(
                    IdentityFeature.this,
                    event.getPlayer(),
                    player -> logger().info(
                            "DataRegistry identity is ready for " + player.getUsername()),
                    "load player identity"
            );
        }
    }
}
