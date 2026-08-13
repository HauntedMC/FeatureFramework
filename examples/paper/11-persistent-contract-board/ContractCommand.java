package com.example.contracts;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import nl.hauntedmc.featureframework.paper.command.brigadier.BrigadierCommand;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.UUID;

/** Thin presentation adapter. ContractBoardService owns validation, async work, and races. */
final class ContractCommand implements BrigadierCommand {
    private final ContractBoardFeature feature;
    private final ContractBoardService service;

    ContractCommand(ContractBoardFeature feature, ContractBoardService service) {
        this.feature = feature;
        this.service = service;
    }

    @Override
    public @NotNull String name() {
        return "contracts";
    }

    @Override
    public @NotNull List<String> aliases() {
        return List.of("contractboard");
    }

    @Override
    public @NotNull LiteralCommandNode<CommandSourceStack> buildTree() {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal(name())
                .requires(source -> source.getSender().hasPermission("network.contracts.use"))
                .executes(context -> list(context.getSource().getSender()));

        root.then(Commands.literal("claim")
                .then(Commands.argument("id", StringArgumentType.word())
                        .executes(context -> claim(
                                context.getSource().getSender(),
                                StringArgumentType.getString(context, "id")))));
        root.then(Commands.literal("post")
                .then(Commands.argument("reward", IntegerArgumentType.integer(1))
                        .then(Commands.argument("description", StringArgumentType.greedyString())
                                .executes(context -> post(
                                        context.getSource().getSender(),
                                        IntegerArgumentType.getInteger(context, "reward"),
                                        StringArgumentType.getString(context, "description"))))));
        return root.build();
    }

    private int list(CommandSender sender) {
        service.openContracts(10).whenComplete((contracts, failure) -> onMain(() -> {
            if (failure != null) {
                invalid(sender, rootMessage(failure));
                return;
            }
            if (contracts.isEmpty()) {
                sender.sendMessage(feature.localization().getMessage("contracts.empty")
                        .forAudience(sender).build());
                return;
            }
            sender.sendMessage(feature.localization().getMessage("contracts.header")
                    .with("count", contracts.size()).forAudience(sender).build());
            contracts.forEach(contract -> sender.sendMessage(feature.localization()
                    .getMessage("contracts.entry")
                    .with("reward", contract.reward())
                    .with("description", contract.description())
                    .with("id", contract.id().toString())
                    .forAudience(sender).build()));
        }));
        return 1;
    }

    private int claim(CommandSender sender, String rawId) {
        if (!(sender instanceof Player player)) return invalid(sender, "Only players can claim contracts");
        UUID contractId;
        try {
            contractId = UUID.fromString(rawId);
        } catch (IllegalArgumentException ignored) {
            return invalid(sender, "Use the complete contract UUID");
        }
        service.claim(contractId, player.getUniqueId()).whenComplete((result, failure) -> onMain(() -> {
            if (failure != null) {
                invalid(sender, rootMessage(failure));
                return;
            }
            String key = result == ContractBoardApi.ClaimResult.CLAIMED
                    ? "contracts.claimed" : "contracts.claim-failed";
            sender.sendMessage(feature.localization().getMessage(key)
                    .with("id", contractId.toString()).forAudience(sender).build());
        }));
        return 1;
    }

    private int post(CommandSender sender, int reward, String description) {
        if (!(sender instanceof Player player)) return invalid(sender, "Only players can post contracts");
        service.post(player.getUniqueId(), description, reward).whenComplete((contract, failure) -> onMain(() -> {
            if (failure != null) {
                invalid(sender, rootMessage(failure));
                return;
            }
            sender.sendMessage(feature.localization().getMessage("contracts.posted")
                    .with("id", contract.id().toString())
                    .with("reward", contract.reward())
                    .forAudience(sender).build());
        }));
        return 1;
    }

    private int invalid(CommandSender sender, String reason) {
        sender.sendMessage(feature.localization().getMessage("contracts.invalid")
                .with("reason", reason).forAudience(sender).build());
        return 0;
    }

    private void onMain(Runnable action) {
        try {
            feature.resources().getTaskManager().scheduleOneTimeTask(action);
        } catch (IllegalStateException ignored) {
            // A completion from the old generation is intentionally dropped during reload/disable.
        }
    }

    private static String rootMessage(Throwable failure) {
        Throwable current = failure;
        while (current.getCause() != null && current.getCause() != current) current = current.getCause();
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
