package com.example.largeplugin;

import nl.hauntedmc.featureframework.api.feature.FeatureDeclaration;
import nl.hauntedmc.featureframework.paper.host.PaperFeature;
import nl.hauntedmc.featureframework.paper.host.PaperFeatureContext;
import org.bukkit.plugin.Plugin;

import java.util.Optional;

@FeatureDeclaration(
        name = "Profiles", version = "1.0.0", enabledByDefault = true, providesCapabilities = PlayerProfileApi.class)
public final class ProfilesFeature extends PaperFeature<Plugin> {
    public ProfilesFeature(PaperFeatureContext<Plugin> context) {
        super(context);
    }

    @Override
    public void initialize() {
        PlayerProfileApi profiles = playerId -> plugin().getServer().getPlayer(playerId) == null
                ? Optional.empty()
                : Optional.of(plugin().getServer().getPlayer(playerId).getName());
        context().services().registerService(PlayerProfileApi.class, profiles);
    }

    @Override public void disable() { }
}
