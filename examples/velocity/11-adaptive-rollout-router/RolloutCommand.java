package com.example.rollouts;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.velocitypowered.api.command.CommandSource;
import nl.hauntedmc.featureframework.velocity.command.brigadier.BrigadierCommand;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;

/** Read-only operations view. Mutating rollout config still goes through feature reload. */
final class RolloutCommand implements BrigadierCommand {
    private final AdaptiveRolloutFeature feature;
    private final RolloutPolicy policy;

    RolloutCommand(AdaptiveRolloutFeature feature, RolloutPolicy policy) {
        this.feature = feature;
        this.policy = policy;
    }

    @Override
    public @NotNull String name() {
        return "rolloutstatus";
    }

    @Override
    public @NotNull LiteralCommandNode<CommandSource> buildTree() {
        return LiteralArgumentBuilder.<CommandSource>literal(name())
                .requires(source -> source.hasPermission("network.rollouts.status"))
                .executes(context -> show(context.getSource()))
                .build();
    }

    private int show(CommandSource source) {
        var snapshot = policy.healthSnapshot();
        source.sendMessage(feature.localization().getMessage("rollout.status-header")
                .with("count", snapshot.size()).forAudience(source).build());
        snapshot.values().stream()
                .sorted(java.util.Comparator.comparing(RolloutRoutingApi.BackendHealth::server))
                .forEach(health -> source.sendMessage(feature.localization().getMessage("rollout.status-entry")
                        .with("server", health.server())
                        .with("healthy", health.healthy())
                        .with("online", health.online())
                        .with("age", Duration.between(health.observedAt(), java.time.Instant.now()).toSeconds())
                        .forAudience(source).build()));
        return 1;
    }
}
