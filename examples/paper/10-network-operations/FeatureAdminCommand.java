package com.example.networkops.paper;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import nl.hauntedmc.featureframework.api.feature.FeatureId;
import nl.hauntedmc.featureframework.command.FeatureCommandModel;
import nl.hauntedmc.featureframework.paper.command.brigadier.BrigadierCommand;
import nl.hauntedmc.featureframework.paper.host.PaperFeature;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

/** A compact control-plane command. Add your own audit log and confirmation policy in production. */
public final class FeatureAdminCommand implements BrigadierCommand {
    private final MyPlugin plugin;

    public FeatureAdminCommand(MyPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String name() {
        return "features";
    }

    @Override
    public @NotNull LiteralCommandNode<CommandSourceStack> buildTree() {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal(name())
                .requires(source -> source.getSender().hasPermission("network.features.use"));

        root.then(Commands.literal("list").executes(context -> list(context.getSource().getSender())));
        root.then(Commands.literal("reload-all")
                .requires(source -> source.getSender().hasPermission("network.features.reload-all"))
                .executes(context -> reloadAll(context.getSource().getSender())));
        root.then(operation("enable", "network.features.enable", (sender, feature) ->
                send(sender, "Enable", feature, plugin.featureHost().enable(FeatureId.of(feature)).result().name())));
        root.then(operation("disable", "network.features.disable", (sender, feature) ->
                send(sender, "Disable", feature, plugin.featureHost().disable(FeatureId.of(feature)).result().name())));
        root.then(operation("softreload", "network.features.reload", (sender, feature) ->
                send(sender, "Soft reload", feature, plugin.featureHost().softReload(FeatureId.of(feature)).result().name())));
        root.then(operation("reload", "network.features.reload", (sender, feature) ->
                send(sender, "Reload", feature, plugin.featureHost().recreate(FeatureId.of(feature)).result().name())));
        root.then(operation("reloadlocal", "network.features.reload-local", this::reloadLocal));
        return root.build();
    }

    private LiteralArgumentBuilder<CommandSourceStack> operation(
            String literal, String permission, Operation operation) {
        return Commands.literal(literal)
                .requires(source -> source.getSender().hasPermission(permission))
                .then(Commands.argument("feature", StringArgumentType.word())
                        .suggests((context, builder) -> suggestFeatures(builder))
                        .executes(context -> {
                            operation.run(context.getSource().getSender(),
                                    StringArgumentType.getString(context, "feature"));
                            return 1;
                        }));
    }

    private int list(CommandSender sender) {
        String names = model().loadedEntries().stream()
                .map(entry -> entry.name())
                .collect(java.util.stream.Collectors.joining(", "));
        sender.sendMessage(Component.text("Loaded features: " + (names.isBlank() ? "none" : names), NamedTextColor.GRAY));
        return 1;
    }

    private int reloadAll(CommandSender sender) {
        send(sender, "Graph reload", "all features", plugin.featureHost().reloadGraph().stage().name());
        return 1;
    }

    private void reloadLocal(CommandSender sender, String requestedName) {
        var id = plugin.featureHost().resolve(requestedName);
        PaperFeature<MyPlugin> feature = id.flatMap(plugin.featureHost()::findLoaded).orElse(null);
        if (feature == null) {
            send(sender, "Local message reload", requestedName, "NOT_LOADED");
            return;
        }
        feature.context().localization().reloadLocalization();
        send(sender, "Local message reload", id.orElseThrow().value(), "SUCCESS");
    }

    private java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestFeatures(
            SuggestionsBuilder builder) {
        model().allSuggestions(builder.getRemainingLowerCase()).forEach(suggestion -> builder.suggest(suggestion.key()));
        return builder.buildFuture();
    }

    private FeatureCommandModel model() {
        return new FeatureCommandModel(plugin.featureHost().features());
    }

    private static void send(CommandSender sender, String operation, String feature, String result) {
        NamedTextColor color = "SUCCESS".equals(result) ? NamedTextColor.GREEN : NamedTextColor.YELLOW;
        sender.sendMessage(Component.text(operation + " " + feature + ": " + result, color));
    }

    @FunctionalInterface
    private interface Operation {
        void run(CommandSender sender, String feature);
    }
}
