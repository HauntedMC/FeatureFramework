package com.example.largeproxy;

import nl.hauntedmc.featureframework.api.feature.FeatureClassification;
import nl.hauntedmc.featureframework.api.feature.FeatureDeclaration;
import nl.hauntedmc.featureframework.velocity.host.VelocityFeature;
import nl.hauntedmc.featureframework.velocity.host.VelocityFeatureContext;

import java.util.Set;
import java.util.stream.Collectors;

@FeatureDeclaration(
        name = "ServerDirectory", version = "1.0.0", enabledByDefault = true,
        classification = FeatureClassification.CAPABILITY_PROVIDER, providesCapabilities = ServerDirectoryApi.class)
public final class ServerDirectoryFeature extends VelocityFeature<Object, Void> {
    public ServerDirectoryFeature(VelocityFeatureContext<Object, Void> context) { super(context); }

    @Override
    public void initialize() {
        ServerDirectoryApi directory = () -> getContext().proxy().getAllServers().stream()
                .map(server -> server.getServerInfo().getName())
                .collect(Collectors.toUnmodifiableSet());
        getContext().services().registerService(ServerDirectoryApi.class, directory);
    }

    @Override public void disable() { }
}
