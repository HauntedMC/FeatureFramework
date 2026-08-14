package nl.hauntedmc.featureframework.paper.ui.hud.actionbar;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ActionBarCycle {
    private final List<ActionBarEntry> entries;
    private final int gapSeconds;

    private ActionBarCycle(List<ActionBarEntry> entries, int gapSeconds) {
        this.entries = List.copyOf(entries);
        this.gapSeconds = Math.max(0, gapSeconds);
    }

    public @NotNull List<ActionBarEntry> entries() {
        return entries;
    }

    public int gapSeconds() {
        return gapSeconds;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final List<ActionBarEntry> entries = new ArrayList<>();
        private int gapSeconds = 0;

        public Builder add(@NotNull ActionBarEntry entry) {
            entries.add(entry);
            return this;
        }

        public Builder gapSeconds(int seconds) {
            this.gapSeconds = Math.max(0, seconds);
            return this;
        }

        public ActionBarCycle build() {
            if (entries.isEmpty()) {
                // harmless 1s placeholder (in practice you’ll always add entries)
                return new ActionBarCycle(Collections.emptyList(), gapSeconds);
            }
            return new ActionBarCycle(entries, gapSeconds);
        }
    }
}
