package nl.hauntedmc.featureframework.paper.ui.hud.actionbar;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

public final class ActionBarEntry {
    private final Component component;
    private final Function<Player, Component> perPlayer;
    private final int seconds;

    private ActionBarEntry(Component component, Function<Player, Component> perPlayer, int seconds) {
        this.component = component;
        this.perPlayer = perPlayer;
        this.seconds = Math.max(0, seconds);
    }

    public static ActionBarEntry of(@NotNull Component component, int seconds) {
        return new ActionBarEntry(component, null, seconds);
    }

    public static ActionBarEntry perPlayer(@NotNull Function<Player, Component> supplier, int seconds) {
        return new ActionBarEntry(null, supplier, seconds);
    }

    public boolean isPerPlayer() {
        return perPlayer != null;
    }

    public Component component() {
        return component;
    }

    public Function<Player, Component> perPlayer() {
        return perPlayer;
    }

    public int seconds() {
        return seconds;
    }
}
