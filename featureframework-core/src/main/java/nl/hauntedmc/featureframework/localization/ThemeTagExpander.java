package nl.hauntedmc.featureframework.localization;

import net.kyori.adventure.text.format.TextColor;
import nl.hauntedmc.featureframework.theme.Theme;
import nl.hauntedmc.featureframework.theme.ThemeColor;
import nl.hauntedmc.featureframework.theme.ThemeItem;
import nl.hauntedmc.featureframework.theme.ThemeRegistry;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/** Expands registered {@code <theme:item>} references into standard MiniMessage colour tags. */
final class ThemeTagExpander {
    private static final int MAX_REPORTED_PROBLEMS = 256;
    private static final Set<String> RESERVED_MINIMESSAGE_TAG_NAMES = Set.of(
            "black", "dark_blue", "dark_green", "dark_aqua", "dark_red", "dark_purple", "gold", "gray",
            "grey", "dark_gray", "dark_grey", "blue", "green", "aqua", "red", "light_purple", "purple",
            "yellow", "white", "color", "bold", "italic", "underlined", "underline", "strikethrough",
            "obfuscated", "click", "hover", "reset", "newline", "br", "gradient", "rainbow", "transition",
            "shadow", "shadow_color", "pride", "keybind", "translate", "tr", "lang", "translate_or", "tr_or",
            "lang_or", "insertion", "font", "selector", "sel", "score", "nbt", "data"
    );

    private final ThemeRegistry themes;
    private final Consumer<String> warningSink;
    private final Set<String> reportedProblems = new HashSet<>();
    private boolean reportLimitReached;

    ThemeTagExpander(ThemeRegistry themes, Consumer<String> warningSink) {
        this.themes = Objects.requireNonNull(themes, "themes");
        this.warningSink = Objects.requireNonNull(warningSink, "warningSink");
        themes.themes().forEach(theme -> {
            if (RESERVED_MINIMESSAGE_TAG_NAMES.contains(theme.id().value().toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("theme identifier conflicts with a MiniMessage tag: "
                        + theme.id().value());
            }
        });
    }

    String expand(String input) {
        if (input == null || input.isEmpty() || input.indexOf('<') < 0 || themes.isEmpty()) return input;

        StringBuilder output = new StringBuilder(input.length());
        Deque<Expansion> openings = new ArrayDeque<>();
        int cursor = 0;
        while (cursor < input.length()) {
            int openingBracket = input.indexOf('<', cursor);
            if (openingBracket < 0) {
                output.append(input, cursor, input.length());
                break;
            }
            output.append(input, cursor, openingBracket);
            if (isEscaped(input, openingBracket)) {
                output.append('<');
                cursor = openingBracket + 1;
                continue;
            }

            int closingBracket = input.indexOf('>', openingBracket + 1);
            if (closingBracket < 0) {
                output.append(input, openingBracket, input.length());
                break;
            }
            String token = input.substring(openingBracket + 1, closingBracket);
            if (token.startsWith("/")) {
                if (!expandClosing(token, openings, output)) output.append(input, openingBracket, closingBracket + 1);
            } else if (!expandOpening(token, openings, output)) {
                output.append(input, openingBracket, closingBracket + 1);
            }
            cursor = closingBracket + 1;
        }
        return output.toString();
    }

    private boolean expandOpening(String token, Deque<Expansion> openings, StringBuilder output) {
        int separator = token.indexOf(':');
        if (separator < 1) {
            Theme theme = theme(token);
            if (theme == null) return false;
            report("malformed:" + token.toLowerCase(Locale.ROOT),
                    "Ignoring malformed theme tag <" + token + ">; expected <" + theme.id().value() + ":item>.");
            openings.push(Expansion.invalid(theme.id().value()));
            return true;
        }

        String themeId = token.substring(0, separator);
        Theme theme = theme(themeId);
        if (theme == null) return false;

        String itemId = token.substring(separator + 1);
        if (itemId.isBlank() || itemId.indexOf(':') >= 0) {
            report("malformed:" + token.toLowerCase(Locale.ROOT),
                    "Ignoring malformed theme tag <" + token + ">; expected <" + theme.id().value() + ":item>.");
            openings.push(Expansion.invalid(theme.id().value()));
            return true;
        }

        ThemeItem item;
        try {
            item = theme.item(itemId).orElse(null);
        } catch (IllegalArgumentException invalidIdentifier) {
            item = null;
        }
        if (item == null) {
            report("unknown:" + theme.id().value().toLowerCase(Locale.ROOT) + ':' + itemId.toLowerCase(Locale.ROOT),
                    "Ignoring unknown theme reference '" + theme.id().value() + ':' + itemId + "'.");
            openings.push(Expansion.invalid(theme.id().value()));
            return true;
        }

        Expansion expansion = tags(theme.id().value(), item.color());
        openings.push(expansion);
        output.append(expansion.openingTag());
        return true;
    }

    private boolean expandClosing(String token, Deque<Expansion> openings, StringBuilder output) {
        String themeId = token.substring(1);
        int separator = themeId.indexOf(':');
        if (separator >= 0) {
            Theme malformedTheme = theme(themeId.substring(0, separator));
            if (malformedTheme == null) return false;
            report("malformed-close:" + token.toLowerCase(Locale.ROOT),
                    "Ignoring malformed theme closing tag <" + token + ">; expected </"
                            + malformedTheme.id().value() + ">.");
            closeCurrentScope(malformedTheme, openings, output);
            return true;
        }
        Theme theme = theme(themeId);
        if (theme == null) return false;
        return closeCurrentScope(theme, openings, output);
    }

    private boolean closeCurrentScope(Theme theme, Deque<Expansion> openings, StringBuilder output) {
        if (openings.isEmpty()) {
            report("unmatched-close:" + theme.id().value().toLowerCase(Locale.ROOT),
                    "Ignoring unmatched </" + theme.id().value() + "> tag.");
            return true;
        }
        Expansion opening = openings.peek();
        if (!opening.themeId().equalsIgnoreCase(theme.id().value())) {
            report("mismatched-close:" + opening.themeId().toLowerCase(Locale.ROOT) + ':'
                            + theme.id().value().toLowerCase(Locale.ROOT),
                    "Ignoring mismatched </" + theme.id().value() + "> tag; expected </" + opening.themeId() + ">.");
            return true;
        }
        output.append(openings.pop().closingTag());
        return true;
    }

    private Theme theme(String themeId) {
        try {
            return themes.theme(themeId).orElse(null);
        } catch (IllegalArgumentException invalidIdentifier) {
            return null;
        }
    }

    private static Expansion tags(String themeId, ThemeColor color) {
        return switch (color) {
            case ThemeColor.Solid solid -> new Expansion(themeId,
                    "<color:" + solid.color().asHexString() + '>', "</color>");
            case ThemeColor.Gradient gradient -> new Expansion(themeId,
                    opening("gradient", gradient.colors(), gradient.phase()), "</gradient>");
            case ThemeColor.Transition transition -> new Expansion(themeId,
                    opening("transition", transition.colors(), transition.phase()), "");
            case ThemeColor.Rainbow rainbow -> {
                String argument = rainbow.reversed() ? "!" + rainbow.phase() : Integer.toString(rainbow.phase());
                yield new Expansion(themeId, rainbow.phase() == 0 && !rainbow.reversed()
                        ? "<rainbow>" : "<rainbow:" + argument + '>', "</rainbow>");
            }
        };
    }

    private static String opening(String name, Iterable<TextColor> colors, double phase) {
        StringBuilder tag = new StringBuilder("<").append(name);
        colors.forEach(color -> tag.append(':').append(color.asHexString()));
        if (Double.compare(phase, 0.0D) != 0) tag.append(':').append(Double.toString(phase));
        return tag.append('>').toString();
    }

    private static boolean isEscaped(String input, int offset) {
        int backslashes = 0;
        for (int index = offset - 1; index >= 0 && input.charAt(index) == '\\'; index--) backslashes++;
        return (backslashes & 1) == 1;
    }

    private synchronized void report(String key, String message) {
        if (reportedProblems.contains(key)) return;
        if (reportedProblems.size() >= MAX_REPORTED_PROBLEMS) {
            if (!reportLimitReached) {
                reportLimitReached = true;
                warningSink.accept("Further distinct theme-tag warnings are suppressed.");
            }
            return;
        }
        if (reportedProblems.add(key)) warningSink.accept(message);
    }

    private record Expansion(String themeId, String openingTag, String closingTag) {
        private static Expansion invalid(String themeId) {
            return new Expansion(themeId, "", "");
        }
    }
}
