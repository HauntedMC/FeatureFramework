package com.example.proxy.activity;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import nl.hauntedmc.featureframework.velocity.host.VelocityFeature;
import nl.hauntedmc.featureframework.velocity.host.VelocityFeatureContext;

import java.time.Duration;

public final class OwnedResourcesFeature extends VelocityFeature<Object, Void> {
    public OwnedResourcesFeature(VelocityFeatureContext<Object, Void> context) {
        super(context);
    }

    @Override
    public void initialize() {
        resources().getListenerManager().registerListener(new LoginListener());
        resources().getTaskManager().scheduleRepeatingTask(
                () -> logger().info("Players online: " + getContext().proxy().getPlayerCount()),
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
