package nl.hauntedmc.featureframework.toolkit.text.format.color;

import java.util.Map;

public class LegacyColorUtils {

    public static final Map<String, Character> TAG_TO_CODE = Map.ofEntries(
            Map.entry("dark_blue", '1'),
            Map.entry("dark_green", '2'),
            Map.entry("dark_aqua", '3'),
            Map.entry("dark_red", '4'),
            Map.entry("dark_purple", '5'),
            Map.entry("gold", '6'),
            Map.entry("gray", '7'),
            Map.entry("grey", '7'), // alias
            Map.entry("dark_gray", '8'),
            Map.entry("dark_grey", '8'), // alias
            Map.entry("blue", '9'),
            Map.entry("green", 'a'),
            Map.entry("aqua", 'b'),
            Map.entry("red", 'c'),
            Map.entry("light_purple", 'd'),
            Map.entry("purple", 'd'), // common alias
            Map.entry("pink", 'd'),  // common alias
            Map.entry("yellow", 'e'),
            Map.entry("white", 'f'),
            Map.entry("bold", 'l'),
            Map.entry("underlined", 'n'),
            Map.entry("underline", 'n'),
            Map.entry("strikethrough", 'm'),
            Map.entry("italic", 'o'),
            Map.entry("obfuscated", 'k'),
            Map.entry("reset", 'r')
    );

    private static final Map<Character, Integer> MC_COLORS = Map.ofEntries(
            Map.entry('0', 0x000000), // black
            Map.entry('1', 0x0000AA), // dark_blue
            Map.entry('2', 0x00AA00), // dark_green
            Map.entry('3', 0x00AAAA), // dark_aqua
            Map.entry('4', 0xAA0000), // dark_red
            Map.entry('5', 0xAA00AA), // dark_purple
            Map.entry('6', 0xFFAA00), // gold
            Map.entry('7', 0xAAAAAA), // gray
            Map.entry('8', 0x555555), // dark_gray
            Map.entry('9', 0x5555FF), // blue
            Map.entry('a', 0x55FF55), // green
            Map.entry('b', 0x55FFFF), // aqua
            Map.entry('c', 0xFF5555), // red
            Map.entry('d', 0xFF55FF), // light_purple
            Map.entry('e', 0xFFFF55), // yellow
            Map.entry('f', 0xFFFFFF)  // white
    );

    /**
     * Replace standard &amp;-style legacy color/format codes with MiniMessage tags.
     */
    public static String convertAmpCodesToMini(String s) {
        return s.replaceAll("(?i)&0", "<black>")
                .replaceAll("(?i)&1", "<dark_blue>")
                .replaceAll("(?i)&2", "<dark_green>")
                .replaceAll("(?i)&3", "<dark_aqua>")
                .replaceAll("(?i)&4", "<dark_red>")
                .replaceAll("(?i)&5", "<dark_purple>")
                .replaceAll("(?i)&6", "<gold>")
                .replaceAll("(?i)&7", "<gray>")
                .replaceAll("(?i)&8", "<dark_gray>")
                .replaceAll("(?i)&9", "<blue>")
                .replaceAll("(?i)&a", "<green>")
                .replaceAll("(?i)&b", "<aqua>")
                .replaceAll("(?i)&c", "<red>")
                .replaceAll("(?i)&d", "<light_purple>")
                .replaceAll("(?i)&e", "<yellow>")
                .replaceAll("(?i)&f", "<white>")
                .replaceAll("(?i)&l", "<bold>")
                .replaceAll("(?i)&n", "<underlined>")
                .replaceAll("(?i)&m", "<strikethrough>")
                .replaceAll("(?i)&o", "<italic>")
                .replaceAll("(?i)&k", "<obfuscated>")
                .replaceAll("(?i)&r", "<reset>");
    }

    /**
     * Replace standard §-style legacy color/format codes with MiniMessage tags.
     */
    public static String convertSecCodesToMini(String s) {
        return s.replaceAll("(?i)§0", "<black>")
                .replaceAll("(?i)§1", "<dark_blue>")
                .replaceAll("(?i)§2", "<dark_green>")
                .replaceAll("(?i)§3", "<dark_aqua>")
                .replaceAll("(?i)§4", "<dark_red>")
                .replaceAll("(?i)§5", "<dark_purple>")
                .replaceAll("(?i)§6", "<gold>")
                .replaceAll("(?i)§7", "<gray>")
                .replaceAll("(?i)§8", "<dark_gray>")
                .replaceAll("(?i)§9", "<blue>")
                .replaceAll("(?i)§a", "<green>")
                .replaceAll("(?i)§b", "<aqua>")
                .replaceAll("(?i)§c", "<red>")
                .replaceAll("(?i)§d", "<light_purple>")
                .replaceAll("(?i)§e", "<yellow>")
                .replaceAll("(?i)§f", "<white>")
                .replaceAll("(?i)§l", "<bold>")
                .replaceAll("(?i)§n", "<underlined>")
                .replaceAll("(?i)§m", "<strikethrough>")
                .replaceAll("(?i)§o", "<italic>")
                .replaceAll("(?i)§k", "<obfuscated>")
                .replaceAll("(?i)§r", "<reset>");
    }

    /**
     * Nearest named legacy colour for a given hex (Euclidean RGB).
     */
    public static char nearestNamedLegacyCode(String hex) {
        int rgb = Integer.parseInt(hex, 16);
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        double best = Double.MAX_VALUE;
        char bestCode = 'f'; // default white
        for (var e : MC_COLORS.entrySet()) {
            int crgb = e.getValue();
            int rr = (crgb >> 16) & 0xFF, gg = (crgb >> 8) & 0xFF, bb = (crgb & 0xFF);
            double d = sq(r - rr) + sq(g - gg) + sq(b - bb);
            if (d < best) {
                best = d;
                bestCode = e.getKey();
            }
        }
        return bestCode;
    }

    private static double sq(int x) {
        return (double) x * x;
    }
}
