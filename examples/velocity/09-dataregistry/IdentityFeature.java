package com.example.registryproxy;

import nl.hauntedmc.featureframework.api.feature.FeatureDeclaration;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import nl.hauntedmc.featureframework.velocity.host.VelocityDataRegistryFeature;
import nl.hauntedmc.featureframework.velocity.host.VelocityFeatureContext;
import nl.hauntedmc.featureframework.velocity.integration.dataregistry.VelocityDataRegistryIdentityGate;

@FeatureDeclaration(
        name = "Identity", version = "1.0.0", enabledByDefault = true, requiresPlugins = "dataregistry")
public final class IdentityFeature extends VelocityDataRegistryFeature<ProxyPlugin, Void> {
    public IdentityFeature(VelocityFeatureContext<ProxyPlugin, Void> context) {
        super(context);
    }

    @Override
    public void initialize() {
        resources().getListenerManager().registerListener(new LoginListener());
    }

    @Override
    public void disable() {
    }

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
