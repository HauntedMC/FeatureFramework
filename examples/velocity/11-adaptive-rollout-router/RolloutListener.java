package com.example.rollouts;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.proxy.server.RegisteredServer;

/** Final platform adapter. It never blocks and never calls Redis from a connection event. */
final class RolloutListener {
    private final AdaptiveRolloutFeature feature;
    private final RolloutPolicy policy;

    RolloutListener(AdaptiveRolloutFeature feature, RolloutPolicy policy) {
        this.feature = feature;
        this.policy = policy;
    }

    @Subscribe(priority = -100)
    public void onPreConnect(ServerPreConnectEvent event) {
        if (!event.getResult().isAllowed()) return;
        String requested = event.getResult().getServer()
                .orElse(event.getOriginalServer()).getServerInfo().getName();
        RolloutRoutingApi.RouteDecision decision = policy.route(event.getPlayer().getUniqueId(), requested);

        if (decision.target().isEmpty()) {
            event.setResult(ServerPreConnectEvent.ServerResult.denied());
            event.getPlayer().sendMessage(feature.localization().getMessage("rollout.unavailable")
                    .forAudience(event.getPlayer()).build());
            return;
        }

        String target = decision.target().orElseThrow();
        if (target.equalsIgnoreCase(requested)) return;
        RegisteredServer replacement = feature.context().proxy().getServer(target).orElse(null);
        if (replacement == null) {
            event.setResult(ServerPreConnectEvent.ServerResult.denied());
            event.getPlayer().sendMessage(feature.localization().getMessage("rollout.misconfigured")
                    .with("server", target).forAudience(event.getPlayer()).build());
            return;
        }

        event.setResult(ServerPreConnectEvent.ServerResult.allowed(replacement));
        event.getPlayer().sendMessage(feature.localization().getMessage("rollout.rerouted")
                .with("server", target)
                .with("reason", decision.reason())
                .forAudience(event.getPlayer()).build());
    }
}
