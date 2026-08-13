package com.example.networkops.velocity;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.velocitypowered.api.command.CommandSource;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import nl.hauntedmc.featureframework.command.FeatureCommandModel;
import nl.hauntedmc.featureframework.loader.FeatureDescriptor;
import nl.hauntedmc.featureframework.velocity.command.brigadier.BrigadierCommand;
import nl.hauntedmc.featureframework.velocity.host.VelocityFeature;
import nl.hauntedmc.featureframework.velocity.host.VelocityFeatureContext;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

/** A compact proxy control-plane command; production code should also audit mutations. */
public final class FeatureAdminCommand implements BrigadierCommand {
    private final ProxyPlugin plugin;

    public FeatureAdminCommand(ProxyPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String name() {
        return "features";
    }

    @Override
    public @NotNull LiteralCommandNode<CommandSource> buildTree() {
        LiteralArgumentBuilder<CommandSource> root = LiteralArgumentBuilder.<CommandSource>literal(name())
                .requires(source -> source.hasPermission("network.features.use"));

        root.then(LiteralArgumentBuilder.<CommandSource>literal("list")
                .executes(context -> list(context.getSource())));
        root.then(LiteralArgumentBuilder.<CommandSource>literal("reload-all")
                .requires(source -> source.hasPermission("network.features.reload-all"))
                .executes(context -> reloadAll(context.getSource())));
        root.then(operation("enable", "network.features.enable", (sender, feature) ->
                send(sender, "Enable", feature, plugin.featureHost().enableFeature(feature).result().name())));
        root.then(operation("disable", "network.features.disable", (sender, feature) ->
                send(sender, "Disable", feature, plugin.featureHost().disableFeature(feature).result().name())));
        root.then(operation("softreload", "network.features.reload", (sender, feature) ->
                send(sender, "Soft reload", feature, plugin.featureHost().softReloadFeature(feature).result().name())));
        root.then(operation("reload", "network.features.reload", (sender, feature) ->
                send(sender, "Reload", feature, plugin.featureHost().reloadFeature(feature).result().name())));
        root.then(operation("reloadlocal", "network.features.reload-local", this::reloadLocal));
        return root.build();
    }

    private LiteralArgumentBuilder<CommandSource> operation(String literal, String permission, Operation operation) {
        return LiteralArgumentBuilder.<CommandSource>literal(literal)
                .requires(source -> source.hasPermission(permission))
                .then(com.mojang.brigadier.builder.RequiredArgumentBuilder
                        .<CommandSource, String>argument("feature", StringArgumentType.word())
                        .suggests((context, builder) -> suggestFeatures(builder))
                        .executes(context -> {
                            operation.run(context.getSource(), StringArgumentType.getString(context, "feature"));
                            return 1;
                        }));
    }

    private int list(CommandSource sender) {
        String names = model().loadedEntries().stream()
                .map(entry -> entry.name())
                .collect(java.util.stream.Collectors.joining(", "));
        sender.sendMessage(Component.text("Loaded features: " + (names.isBlank() ? "none" : names), NamedTextColor.GRAY));
        return 1;
    }

    private int reloadAll(CommandSource sender) {
        send(sender, "Graph reload", "all features", plugin.featureHost().reload().stage().name());
        return 1;
    }

    private void reloadLocal(CommandSource sender, String requestedName) {
        String key = plugin.featureHost().managedHost().resolveFeatureKey(requestedName);
        VelocityFeature<Object, Void> feature = key == null
                ? null
                : plugin.featureHost().managedHost().registry().getLoadedFeature(key);
        if (feature == null) {
            send(sender, "Local message reload", requestedName, "NOT_LOADED");
            return;
        }
        feature.getContext().localization().reloadLocalization();
        send(sender, "Local message reload", key, "SUCCESS");
    }

    private CompletableFuture<Suggestions> suggestFeatures(SuggestionsBuilder builder) {
        model().allSuggestions(builder.getRemainingLowerCase()).forEach(suggestion -> builder.suggest(suggestion.key()));
        return builder.buildFuture();
    }

    private FeatureCommandModel<VelocityFeature<Object, Void>,
            FeatureDescriptor<VelocityFeature<Object, Void>, VelocityFeatureContext<Object, Void>>> model() {
        return new FeatureCommandModel<>(
                plugin.featureHost().managedHost().registry(),
                plugin.featureHost().managedHost()::resolveFeatureKey);
    }

    private static void send(CommandSource sender, String operation, String feature, String result) {
        NamedTextColor color = "SUCCESS".equals(result) ? NamedTextColor.GREEN : NamedTextColor.YELLOW;
        sender.sendMessage(Component.text(operation + " " + feature + ": " + result, color));
    }

    @FunctionalInterface
    private interface Operation {
        void run(CommandSource sender, String feature);
    }
}
