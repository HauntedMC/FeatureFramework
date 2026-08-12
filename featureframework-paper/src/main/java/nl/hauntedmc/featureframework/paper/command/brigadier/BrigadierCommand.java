package nl.hauntedmc.featureframework.paper.command.brigadier;

import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/** Root-literal Brigadier command contributed by a managed Paper feature. */
public interface BrigadierCommand {
    @NotNull String name();
    @NotNull LiteralCommandNode<CommandSourceStack> buildTree();
    default List<String> aliases() { return List.of(); }
    default String description() { return null; }
}
