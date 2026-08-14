package com.example.myplugin.activity;

import nl.hauntedmc.featureframework.api.feature.FeatureDeclaration;
import nl.hauntedmc.featureframework.paper.host.PaperFeature;
import nl.hauntedmc.featureframework.paper.host.PaperFeatureContext;
import nl.hauntedmc.featureframework.paper.time.BukkitTime;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;

@FeatureDeclaration(name = "Activity", version = "1.0.0", enabledByDefault = true)
public final class ActivityFeature extends PaperFeature<Plugin> {
    public ActivityFeature(PaperFeatureContext<Plugin> context) {
        super(context);
    }

    @Override
    public void initialize() {
        resources().listeners().registerListener(new JoinListener());
        resources().tasks().scheduleRepeatingTask(
                () -> logger().info("Activity heartbeat"),
                BukkitTime.seconds(30)
        );
    }

    @Override
    public void disable() {
    }

    private final class JoinListener implements Listener {
        @EventHandler
        public void onJoin(PlayerJoinEvent event) {
            logger().info(event.getPlayer().getName() + " joined");
        }
    }
}
