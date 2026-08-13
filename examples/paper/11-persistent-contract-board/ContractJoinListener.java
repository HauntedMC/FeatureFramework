package com.example.contracts;

import nl.hauntedmc.featureframework.paper.time.BukkitTime;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/** Uses the last-known count only; a join event never waits for MySQL. */
final class ContractJoinListener implements Listener {
    private final ContractBoardFeature feature;
    private final ContractBoardService service;

    ContractJoinListener(ContractBoardFeature feature, ContractBoardService service) {
        this.feature = feature;
        this.service = service;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        int open = service.lastKnownOpenCount();
        int minimum = feature.getConfigHandler().get("join-notice.minimum-open", Integer.class, 3);
        if (open < minimum) return;

        feature.resources().getTaskManager().scheduleDelayedTask(() -> {
            if (!event.getPlayer().isOnline()) return;
            event.getPlayer().sendMessage(feature.localization().getMessage("contracts.join-notice")
                    .with("count", open).forAudience(event.getPlayer()).build());
        }, BukkitTime.seconds(2));
    }
}
