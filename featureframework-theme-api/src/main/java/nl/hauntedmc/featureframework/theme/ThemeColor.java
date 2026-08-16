package nl.hauntedmc.featureframework.theme;

import net.kyori.adventure.text.format.TextColor;

import java.util.List;
import java.util.Objects;

/** A validated MiniMessage colour operation that can be referenced by a theme item. */
public sealed interface ThemeColor permits ThemeColor.Solid, ThemeColor.Gradient,
        ThemeColor.Transition, ThemeColor.Rainbow {

    static Solid solid(TextColor color) {
        return new Solid(color);
    }

    static Solid solid(int rgb) {
        if (rgb < 0 || rgb > 0xFFFFFF) {
            throw new IllegalArgumentException("rgb must be in the inclusive range [0x000000, 0xFFFFFF]");
        }
        return solid(TextColor.color(rgb));
    }

    static Solid solid(String hex) {
        String value = Objects.requireNonNull(hex, "hex").trim();
        TextColor color = TextColor.fromHexString(value);
        if (color == null || value.length() != 7 || value.charAt(0) != '#') {
            throw new IllegalArgumentException("hex must use the #RRGGBB format");
        }
        return solid(color);
    }

    static Gradient gradient(List<? extends TextColor> colors) {
        return new Gradient(copyColors(colors), 0.0D);
    }

    static Gradient gradient(List<? extends TextColor> colors, double phase) {
        return new Gradient(copyColors(colors), phase);
    }

    static Transition transition(List<? extends TextColor> colors, double phase) {
        return new Transition(copyColors(colors), phase);
    }

    static Transition transition(List<? extends TextColor> colors) {
        return transition(colors, 0.0D);
    }

    static Rainbow rainbow() {
        return new Rainbow(0, false);
    }

    static Rainbow rainbow(int phase, boolean reversed) {
        return new Rainbow(phase, reversed);
    }

    /** A persistent solid text colour. */
    record Solid(TextColor color) implements ThemeColor {
        public Solid {
            Objects.requireNonNull(color, "color");
        }
    }

    /** A scoped gradient with at least two colours and a phase between -1 and 1. */
    record Gradient(List<TextColor> colors, double phase) implements ThemeColor {
        public Gradient {
            colors = copyColors(colors);
            phase = validatePhase(phase);
            requireAtLeastTwo(colors, "gradient");
        }
    }

    /** A transition colour with at least two colours and a phase between -1 and 1. */
    record Transition(List<TextColor> colors, double phase) implements ThemeColor {
        public Transition {
            colors = copyColors(colors);
            phase = validatePhase(phase);
            requireAtLeastTwo(colors, "transition");
        }
    }

    /** A scoped MiniMessage rainbow. */
    record Rainbow(int phase, boolean reversed) implements ThemeColor {
    }

    private static List<TextColor> copyColors(List<? extends TextColor> colors) {
        Objects.requireNonNull(colors, "colors");
        return colors.stream().map(color -> Objects.requireNonNull(color, "color")).toList();
    }

    private static double validatePhase(double phase) {
        if (!Double.isFinite(phase) || phase < -1.0D || phase > 1.0D) {
            throw new IllegalArgumentException("phase must be finite and in the inclusive range [-1, 1]");
        }
        return phase;
    }

    private static void requireAtLeastTwo(List<TextColor> colors, String type) {
        if (colors.size() < 2) {
            throw new IllegalArgumentException(type + " requires at least two colors");
        }
    }
}
