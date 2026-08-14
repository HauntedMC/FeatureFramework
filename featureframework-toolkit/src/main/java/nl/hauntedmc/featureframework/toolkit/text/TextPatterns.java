package nl.hauntedmc.featureframework.toolkit.text;

import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;

/** Shared immutable patterns used by text formatting and validation helpers. */
public final class TextPatterns {
    public static final Pattern AMP_CODES = Pattern.compile("(?i)&([0-9a-fk-or])");
    public static final Pattern SEC_CODES = Pattern.compile("(?i)§([0-9a-fk-or])");
    public static final Pattern POUND_HEX = Pattern.compile("(?i)&#([0-9a-f]{6})");
    public static final Pattern SECTION_POUND_HEX = Pattern.compile("(?i)§#([0-9a-f]{6})");
    public static final Pattern AMP_BUNGEE_HEX = Pattern.compile("(?i)&x(?:&[0-9a-f]){6}");
    public static final Pattern SEC_BUNGEE_HEX = Pattern.compile("(?i)§x(?:§[0-9a-f]){6}");
    public static final Pattern MINI_HEX_TAG = Pattern.compile("(?i)<#([0-9a-f]{6})>");
    public static final Pattern MINI_HEX_DOUBLE = Pattern.compile("(?i)<##([0-9a-f]{6})>");
    public static final Pattern ANY_MINI_TAG = Pattern.compile("(?s)<[^>]+>");
    public static final Pattern HEX_TAG = Pattern.compile("(?i)<#([0-9a-f]{6})>");
    public static final Pattern OPEN_TAG = Pattern.compile("(?i)<([a-z_][a-z0-9_\\-]*)[^>]*>");
    public static final Pattern CLOSE_TAG = Pattern.compile("(?i)</([a-z_][a-z0-9_\\-]*)\\s*>");
    public static final Pattern NEWLINE_TAG = Pattern.compile("(?i)<(newline|br)>");
    public static final Pattern URL = Pattern.compile("(?i)\\b((?:https?://|www\\.)[^\\s<>]+)");
    public static final Pattern MC_NAME = Pattern.compile("^[A-Za-z0-9_]{3,16}$");
    public static final Pattern BUKKIT_ALIAS_FORMAT = Pattern.compile("^[a-z0-9_\\-]+$");
    public static final Pattern MC_IN_VERSION = Pattern.compile("\\(MC:\\s*([0-9]+(?:\\.[0-9]+){1,2})\\)");
    public static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    public static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("dd-MM-yyyy_HHmmss");
    public static final Pattern DATE_IN_NAME = Pattern.compile(".*?(\\d{2}-\\d{2}-\\d{4}).*");

    private TextPatterns() { }
}
