package com.example.largeproxy;

import nl.hauntedmc.featureframework.api.feature.FeatureDeclaration;
import nl.hauntedmc.featureframework.velocity.host.VelocityFeature;
import nl.hauntedmc.featureframework.velocity.host.VelocityFeatureContext;

import java.util.Set;
import java.util.stream.Collectors;

@FeatureDeclaration(
        name = "ServerDirectory", version = "1.0.0", enabledByDefault = true, providesCapabilities = ServerDirectoryApi.class)
public final class ServerDirectoryFeature extends VelocityFeature<Object> {
    public ServerDirectoryFeature(VelocityFeatureContext<Object> context) { super(context); }

    @Override
    public void initialize() {
        ServerDirectoryApi directory = () -> context().proxy().getAllServers().stream()
                .map(server -> server.getServerInfo().getName())
                .collect(Collectors.toUnmodifiableSet());
        context().services().registerService(ServerDirectoryApi.class, directory);
    }

    @Override public void disable() { }
}
