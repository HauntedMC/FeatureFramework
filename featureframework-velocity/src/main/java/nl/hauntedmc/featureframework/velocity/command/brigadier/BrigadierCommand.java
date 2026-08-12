package nl.hauntedmc.featureframework.velocity.command.brigadier;

import com.mojang.brigadier.tree.LiteralCommandNode;
import com.velocitypowered.api.command.CommandSource;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Root-literal Brigadier command contributed by a proxy feature.
 * Mirrors the Bukkit-side shape but targets Velocity's CommandSource.
 */
public interface BrigadierCommand {

    /**
     * Root literal name, e.g. "pfriends" (avoid collisions with other commands).
     */
    @NotNull String name();

    /**
     * Build the full Brigadier tree for this root literal.
     */
    @NotNull LiteralCommandNode<CommandSource> buildTree();

    /**
     * Optional aliases for the root literal.
     */
    default List<String> aliases() {
        return List.of();
    }

    /**
     * Optional description shown by some clients/registrars.
     */
    default String description() {
        return null;
    }
}
