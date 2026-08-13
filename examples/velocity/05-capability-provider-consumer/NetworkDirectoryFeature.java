package com.example.proxy;

import com.example.network.api.NetworkPlayerApi;
import nl.hauntedmc.featureframework.velocity.host.VelocityFeature;
import nl.hauntedmc.featureframework.velocity.host.VelocityFeatureContext;

public final class NetworkDirectoryFeature extends VelocityFeature<Object, Void> {
    public NetworkDirectoryFeature(VelocityFeatureContext<Object, Void> context) {
        super(context);
    }

    @Override
    public void initialize() {
        NetworkPlayerApi players = playerId -> getContext().proxy()
                .getPlayer(playerId)
                .flatMap(player -> player.getCurrentServer()
                        .map(connection -> connection.getServerInfo().getName()));

        getContext().services().registerService(NetworkPlayerApi.class, players);
    }

    @Override
    public void disable() {
    }
}
