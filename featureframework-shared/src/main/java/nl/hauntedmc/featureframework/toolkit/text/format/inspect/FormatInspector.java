package nl.hauntedmc.featureframework.toolkit.text.format.inspect;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.KeybindComponent;
import net.kyori.adventure.text.NBTComponent;
import net.kyori.adventure.text.ScoreComponent;
import net.kyori.adventure.text.SelectorComponent;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import nl.hauntedmc.featureframework.toolkit.text.TextPatterns;
import nl.hauntedmc.featureframework.toolkit.text.format.TextFormatter;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Utilities to detect whether text or components contain formatting/markup.
 *
 * <p>String detection supports the same "shape" taxonomy as {@link TextFormatter.InputFormat}.
 * Component detection inspects Adventure styles and event-bearing component types.</p>
 */
public final class FormatInspector {
    private FormatInspector() {
    }

    // ==== String detection ====

    /**
     * Returns true if {@code s} contains any formatting of the requested kinds.
     * Provide a subset of {@link TextFormatter.InputFormat} to narrow detection.
     */
    public static boolean containsFormatting(String s, Set<TextFormatter.InputFormat> kinds) {
        if (s == null || s.isEmpty() || kinds == null || kinds.isEmpty()) return false;

        boolean wantMini = kinds.contains(TextFormatter.InputFormat.MINIMESSAGE) || kinds.contains(TextFormatter.InputFormat.HEX_MINI);
        boolean wantAmp = kinds.contains(TextFormatter.InputFormat.LEGACY_AMPERSAND) || kinds.contains(TextFormatter.InputFormat.HEX_BUNGEE_AMP);
        boolean wantSec = kinds.contains(TextFormatter.InputFormat.LEGACY_SECTION) || kinds.contains(TextFormatter.InputFormat.HEX_BUNGEE_SECTION);

        if (kinds.contains(TextFormatter.InputFormat.HEX_BUNGEE_AMP) && TextPatterns.AMP_BUNGEE_HEX.matcher(s).find())
            return true;
        if (kinds.contains(TextFormatter.InputFormat.HEX_BUNGEE_SECTION) && TextPatterns.SEC_BUNGEE_HEX.matcher(s).find())
            return true;

        if (wantAmp && TextPatterns.AMP_CODES.matcher(s).find()) return true;
        if (wantSec && TextPatterns.SEC_CODES.matcher(s).find()) return true;

        if (kinds.contains(TextFormatter.InputFormat.HEX_POUND) && (TextPatterns.POUND_HEX.matcher(s).find() || TextPatterns.SECTION_POUND_HEX.matcher(s).find()))
            return true;
        if (kinds.contains(TextFormatter.InputFormat.HEX_MINI) && TextPatterns.MINI_HEX_TAG.matcher(s).find())
            return true;

        return wantMini && TextPatterns.ANY_MINI_TAG.matcher(s).find();
    }

    /**
     * Convenience: detect any known formatting (legacy + MiniMessage + hex variants).
     */
    public static boolean containsAnyFormatting(String s) {
        return containsFormatting(s, EnumSet.of(
                TextFormatter.InputFormat.LEGACY_AMPERSAND,
                TextFormatter.InputFormat.LEGACY_SECTION,
                TextFormatter.InputFormat.HEX_POUND,
                TextFormatter.InputFormat.HEX_BUNGEE_AMP,
                TextFormatter.InputFormat.HEX_BUNGEE_SECTION,
                TextFormatter.InputFormat.HEX_MINI,
                TextFormatter.InputFormat.MINIMESSAGE
        ));
    }

    // ==== Component detection ====

    /**
     * Aspects of Adventure formatting to test for on a {@link Component}.
     */
    public enum ComponentAspect {
        COLOR,
        DECORATION,
        EVENTS,        // click/hover
        INSERTION,
        FONT,
        KEYBIND,
        TRANSLATABLE,
        SCORE,
        SELECTOR,
        NBT
    }

    /**
     * Returns true if the component (or any child) has any formatting of the requested aspects.
     * If {@code aspects} is empty, all aspects are considered.
     */
    public static boolean hasFormatting(Component component, Set<ComponentAspect> aspects) {
        Objects.requireNonNull(component, "component");
        final Set<ComponentAspect> as = (aspects == null || aspects.isEmpty())
                ? EnumSet.allOf(ComponentAspect.class)
                : EnumSet.copyOf(aspects);
        return hasFormattingRecursive(component, as);
    }

    public static boolean hasAnyFormatting(Component component) {
        return hasFormatting(component, EnumSet.allOf(ComponentAspect.class));
    }

    private static boolean hasFormattingRecursive(Component c, Set<ComponentAspect> aspects) {
        // Style checks
        Style st = c.style();
        if (aspects.contains(ComponentAspect.COLOR)) {
            TextColor color = st.color();
            if (color != null) return true;
        }
        if (aspects.contains(ComponentAspect.DECORATION)) {
            for (TextDecoration dec : TextDecoration.values()) {
                if (st.decoration(dec) == TextDecoration.State.TRUE) return true;
            }
        }
        if (aspects.contains(ComponentAspect.EVENTS)) {
            ClickEvent<?> ce = st.clickEvent();
            HoverEvent<?> he = st.hoverEvent();
            if (ce != null || he != null) return true;
        }
        if (aspects.contains(ComponentAspect.INSERTION) && st.insertion() != null) return true;
        if (aspects.contains(ComponentAspect.FONT) && st.font() != null) return true;

        // Type-specific checks
        if (aspects.contains(ComponentAspect.KEYBIND) && (c instanceof KeybindComponent)) return true;
        if (aspects.contains(ComponentAspect.TRANSLATABLE) && (c instanceof TranslatableComponent)) return true;
        if (aspects.contains(ComponentAspect.SCORE) && (c instanceof ScoreComponent)) return true;
        if (aspects.contains(ComponentAspect.SELECTOR) && (c instanceof SelectorComponent)) return true;
        if (aspects.contains(ComponentAspect.NBT) && (c instanceof NBTComponent<?>)) return true;

        // Children
        for (Component child : c.children()) {
            if (hasFormattingRecursive(child, aspects)) return true;
        }
        return false;
    }
}
