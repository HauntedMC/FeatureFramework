package nl.hauntedmc.featureframework.hud;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;

/** Immutable content; no platform entities or mutable Adventure boss bars cross this boundary. */
public sealed interface HudFrame {
    HudService.Channel channel();

    record Sidebar(Component title, List<Component> lines) implements HudFrame {
        public Sidebar {
            Objects.requireNonNull(title);
            lines = List.copyOf(lines);
            if (lines.size() > 15) throw new IllegalArgumentException("A sidebar has at most 15 lines");
        }
        public HudService.Channel channel() { return HudService.Channel.SIDEBAR; }
    }
    record ActionBar(Component text) implements HudFrame {
        public ActionBar { Objects.requireNonNull(text); }
        public HudService.Channel channel() { return HudService.Channel.ACTION_BAR; }
    }
    record Bar(Component title, float progress, BossBar.Color color, BossBar.Overlay overlay,
               java.util.Set<BossBar.Flag> flags) {
        public Bar(Component title,float progress,BossBar.Color color,BossBar.Overlay overlay) {
            this(title,progress,color,overlay,java.util.Set.of());
        }
        public Bar {
            Objects.requireNonNull(title);
            flags=java.util.Set.copyOf(flags);
            Objects.requireNonNull(color);
            Objects.requireNonNull(overlay);
            if (!Float.isFinite(progress)) throw new IllegalArgumentException("Non-finite progress");
            progress = Math.clamp(progress, 0, 1);
        }
    }
    record Bars(List<Bar> bars) implements HudFrame {
        public Bars {
            bars = List.copyOf(bars);
            if (bars.size() > 2) throw new IllegalArgumentException("At most two managed bars");
        }
        public HudService.Channel channel() { return HudService.Channel.BOSS_BAR; }
    }
    record Title(Component title, Component subtitle, Duration fadeIn, Duration stay,
                 Duration fadeOut) implements HudFrame {
        public Title {
            Objects.requireNonNull(title);
            Objects.requireNonNull(subtitle);
            for (Duration time : List.of(fadeIn, stay, fadeOut))
                if (time.isNegative()) throw new IllegalArgumentException("Negative title duration");
        }
        public HudService.Channel channel() { return HudService.Channel.TITLE; }
    }
}
