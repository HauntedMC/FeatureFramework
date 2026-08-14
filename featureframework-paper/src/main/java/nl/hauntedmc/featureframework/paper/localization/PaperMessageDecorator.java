package nl.hauntedmc.featureframework.paper.localization;

import org.bukkit.entity.Player;

/** Optional platform placeholder expansion applied before component rendering. */
@FunctionalInterface
public interface PaperMessageDecorator {
    String decorate(String message, Player player);

    static PaperMessageDecorator identity() { return (message, player) -> message; }
}
