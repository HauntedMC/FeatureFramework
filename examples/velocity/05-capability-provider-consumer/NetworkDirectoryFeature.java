package com.example.proxy;

import com.example.network.api.NetworkPlayerApi;
import nl.hauntedmc.featureframework.api.feature.FeatureDeclaration;
import nl.hauntedmc.featureframework.api.feature.FeatureScope;
import nl.hauntedmc.featureframework.velocity.host.VelocityFeature;
import nl.hauntedmc.featureframework.velocity.host.VelocityFeatureContext;

@FeatureDeclaration(
        scope = FeatureScope.NODE,
        name = "NetworkDirectory", version = "1.0.0", enabledByDefault = true, providesCapabilities = NetworkPlayerApi.class)
public final class NetworkDirectoryFeature extends VelocityFeature<Object> {
    public NetworkDirectoryFeature(VelocityFeatureContext<Object> context) {
        super(context);
    }

    @Override
    public void initialize() {
        NetworkPlayerApi players = playerId -> context().proxy()
                .getPlayer(playerId)
                .flatMap(player -> player.getCurrentServer()
                        .map(connection -> connection.getServerInfo().getName()));

        services().publish(NetworkPlayerApi.class, players);
    }

    @Override
    public void disable() {
    }
}
