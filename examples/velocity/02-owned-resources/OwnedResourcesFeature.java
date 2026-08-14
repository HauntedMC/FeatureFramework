package com.example.proxy.activity;

import nl.hauntedmc.featureframework.api.feature.FeatureDeclaration;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import nl.hauntedmc.featureframework.velocity.host.VelocityFeature;
import nl.hauntedmc.featureframework.velocity.host.VelocityFeatureContext;

import java.time.Duration;

@FeatureDeclaration(name = "Activity", version = "1.0.0", enabledByDefault = true)
public final class OwnedResourcesFeature extends VelocityFeature<Object> {
    public OwnedResourcesFeature(VelocityFeatureContext<Object> context) {
        super(context);
    }

    @Override
    public void initialize() {
        resources().listeners().registerListener(new LoginListener());
        resources().tasks().scheduleRepeatingTask(
                () -> logger().info("Players online: " + context().proxy().getPlayerCount()),
                Duration.ofSeconds(30)
        );
    }

    @Override
    public void disable() {
    }

    private final class LoginListener {
        @Subscribe
        public void onPostLogin(PostLoginEvent event) {
            logger().info(event.getPlayer().getUsername() + " connected");
        }
    }
}
