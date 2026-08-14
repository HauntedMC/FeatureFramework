package nl.hauntedmc.featureframework.toolkit.text.format;

import net.kyori.adventure.text.minimessage.MiniMessage;
import nl.hauntedmc.featureframework.toolkit.text.TextPatterns;
import nl.hauntedmc.featureframework.toolkit.text.format.color.LegacyColorUtils;
import nl.hauntedmc.featureframework.toolkit.text.format.constants.FormatConstants;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@code TextCodec} — canonical string-to-string converter between legacy, MiniMessage, and plain text.
 *
 * <h2>What it does</h2>
 * <ul>
 *   <li>Normalizes mixed legacy/MiniMessage input to MiniMessage (<b>string</b>), for safe parsing later.</li>
 *   <li>Converts MiniMessage back to legacy (choose '&amp;' or '§', choose hex output style, or downsample).</li>
 *   <li>Strips legacy and/or MiniMessage tags to produce plain text.</li>
 *   <li>Provides escaping to prevent MiniMessage tag injection.</li>
 * </ul>
 *
 * <p><b>Why:</b> Keep all format conversions in one place. Use {@link ComponentFormatter} for string → {@code Component} parsing.</p>
 *
 * <p><b>Thread-safety:</b> The class is stateless; each {@link Converter} is single-use and not thread-safe.</p>
 */
public final class TextFormatter {
    private TextFormatter() {
    }

    /* ========================= Public entrypoints ========================= */

    /**
     * Start a new conversion pipeline.
     *
     * @param input raw input string (legacy/MiniMessage/plain)
     * @return a fluent {@link Converter}
     */
    public static Converter convert(String input) {
        return new Converter(input);
    }

    /**
     * Shortcut: normalize to MiniMessage using {@link InputFormat#MIXED_INPUT} expectations.
     */
    public static String toMiniMessage(String input) {
        return convert(input).expect(InputFormat.MIXED_INPUT).toMiniMessage();
    }

    /**
     * Shortcut: normalize and serialize to legacy using '&amp;' codes.
     */
    public static String toLegacyAmpersand(String input) {
        return convert(input).expect(InputFormat.ANY).toLegacy(FormatConstants.AMP_CHAR);
    }

    /**
     * Shortcut: normalize and serialize to legacy using '§' codes.
     */
    public static String toLegacySection(String input) {
        return convert(input).expect(InputFormat.ANY).toLegacy(FormatConstants.SECTION_CHAR);
    }

    /**
     * Shortcut: normalize and strip to plain text.
     */
    public static String toPlain(String input) {
        return convert(input).expect(InputFormat.ANY).toPlain();
    }

    /* ========================= Input format modelling ========================= */

    /**
     * Hints describing which input shapes to expect (can be combined).
     * These hints avoid unnecessary work and steer which transforms to apply.
     */
    public enum InputFormat {
        /**
         * {@code &0..&f}, {@code &k..&o}, {@code &r}.
         */
        LEGACY_AMPERSAND,
        /**
         * {@code §0..§f}, {@code §k..§o}, {@code §r}.
         */
        LEGACY_SECTION,
        /**
         * {@code &#RRGGBB} and/or {@code §#RRGGBB}.
         */
        HEX_POUND,
        /**
         * Bungee hex: {@code &x&F&F&F&F&F&F}.
         */
        HEX_BUNGEE_AMP,
        /**
         * Bungee hex: {@code §x§F§F§F§F§F§F}.
         */
        HEX_BUNGEE_SECTION,
        /**
         * MiniMessage hex: {@code <#RRGGBB>}.
         */
        HEX_MINI,
        /**
         * Non-standard: {@code <##RRGGBB>} — will be normalized to {@code <#RRGGBB>}.
         */
        HEX_MINI_DOUBLE,
        /**
         * General MiniMessage tags (colors, decorations, etc.).
         */
        MINIMESSAGE,
        /**
         * Plain text (no styling).
         */
        PLAIN;

        /**
         * Common preset for legacy-heavy input.
         */
        public static final Set<InputFormat> ANY_LEGACY = EnumSet.of(
                LEGACY_AMPERSAND, LEGACY_SECTION, HEX_POUND, HEX_BUNGEE_AMP, HEX_BUNGEE_SECTION
        );
        /**
         * All known hex shapes.
         */
        public static final Set<InputFormat> HEX_ALL = EnumSet.of(
                HEX_POUND, HEX_BUNGEE_AMP, HEX_BUNGEE_SECTION, HEX_MINI, HEX_MINI_DOUBLE
        );
        /**
         * Safe default for “mixed” strings (legacy + MiniMessage).
         */
        public static final Set<InputFormat> MIXED_INPUT = EnumSet.of(
                LEGACY_AMPERSAND, LEGACY_SECTION, HEX_POUND, HEX_BUNGEE_AMP, HEX_BUNGEE_SECTION,
                HEX_MINI, MINIMESSAGE
        );
        /**
         * Everything.
         */
        public static final Set<InputFormat> ANY = EnumSet.allOf(InputFormat.class);
    }

    /* ========================= Options / policy ========================= */

    /**
     * Options influencing how text is normalized and serialized.
     * <p>Use {@link #builder()} to tweak from the defaults.</p>
     */
    public static final class Options {
        /**
         * If true (default), normalize {@code §} to {@code &} prior to legacy transforms.
         */
        public final boolean normalizeSectionToAmpersand;
        /**
         * If true (default), convert legacy hex variants to {@code <#RRGGBB>}.
         */
        public final boolean transformHex;
        /**
         * If true (default), convert standard {@code &/§} color/format codes to MiniMessage tags.
         */
        public final boolean transformStandardCodes;

        /**
         * MiniMessage→legacy: if true (default), emit {@code &r}/{@code §r} for closing tags.
         */
        public final boolean legacyEmitResetOnClose;
        /**
         * Legacy output: if true (default), keep full hex; if false, downsample to nearest named color.
         */
        public final boolean legacyOutputHexColors;
        /**
         * Legacy output: if true, serialize hex as Bungee’s {@code &x&F&F&...}; else {@code &#RRGGBB}.
         */
        public final boolean xRepeatedHexForLegacyOutput;

        private Options(Builder b) {
            this.normalizeSectionToAmpersand = b.normalizeSectionToAmpersand;
            this.transformHex = b.transformHex;
            this.transformStandardCodes = b.transformStandardCodes;
            this.legacyEmitResetOnClose = b.legacyEmitResetOnClose;
            this.legacyOutputHexColors = b.legacyOutputHexColors;
            this.xRepeatedHexForLegacyOutput = b.xRepeatedHexForLegacyOutput;
        }

        /**
         * Default options (safe, lossless).
         */
        public static Options all() {
            return builder().build();
        }

        /**
         * Create a builder with default values.
         */
        public static Builder builder() {
            return new Builder();
        }

        /**
         * Builder for {@link Options}.
         * <p>Defaults favor safety and fidelity.</p>
         */
        public static final class Builder {
            private boolean normalizeSectionToAmpersand = true;
            private boolean transformHex = true;
            private boolean transformStandardCodes = true;

            private boolean legacyEmitResetOnClose = true; // safer default for scope
            private boolean legacyOutputHexColors = true;
            private boolean xRepeatedHexForLegacyOutput = false;

            /**
             * Normalize {@code §} to {@code &} before transforms (default: true).
             */
            public Builder normalizeSectionToAmpersand(boolean v) {
                this.normalizeSectionToAmpersand = v;
                return this;
            }

            /**
             * Convert legacy hex to {@code <#RRGGBB>} (default: true).
             */
            public Builder transformHex(boolean v) {
                this.transformHex = v;
                return this;
            }

            /**
             * Convert standard {@code &/§} codes to MiniMessage tags (default: true).
             */
            public Builder transformStandardCodes(boolean v) {
                this.transformStandardCodes = v;
                return this;
            }

            /**
             * Emit reset on closing tags when serializing to legacy (default: true).
             */
            public Builder legacyEmitResetOnClose(boolean v) {
                this.legacyEmitResetOnClose = v;
                return this;
            }

            /**
             * Keep hex colors in legacy output (default: true).
             */
            public Builder legacyOutputHexColors(boolean v) {
                this.legacyOutputHexColors = v;
                return this;
            }

            /**
             * Use Bungee’s {@code &x} hex format in legacy output (default: false).
             */
            public Builder xRepeatedHex(boolean v) {
                this.xRepeatedHexForLegacyOutput = v;
                return this;
            }

            /**
             * Build the immutable {@link Options}.
             */
            public Options build() {
                return new Options(this);
            }
        }
    }

    /* ========================= Converter (builder) ========================= */

    /**
     * Fluent builder performing the conversion steps.
     * <p>Call one of {@link #toMiniMessage()}, {@link #toLegacy(char)} or {@link #toPlain()} to execute.</p>
     */
    public static final class Converter {
        private final String originalInput;
        private final EnumSet<InputFormat> expects = EnumSet.noneOf(InputFormat.class);
        private Options options = Options.all();
        private UnaryOperator<String> preprocessor; // e.g., PAPI

        private Converter(String input) {
            this.originalInput = input;
        }

        /**
         * Declare expected input formats (multiple allowed). Defaults to {@link InputFormat#MIXED_INPUT}.
         *
         * @param formats expected formats to optimize and guide conversion
         * @return this builder
         */
        public Converter expect(InputFormat... formats) {
            if (formats != null) for (InputFormat f : formats) if (f != null) expects.add(f);
            return this;
        }

        /**
         * Declare expected input formats from a prebuilt set.
         *
         * @param formats set of expected formats
         * @return this builder
         */
        public Converter expect(Set<InputFormat> formats) {
            if (formats != null) expects.addAll(formats);
            return this;
        }

        /**
         * Optional preprocessor applied to the raw input before any transforms (e.g., PlaceholderAPI).
         *
         * @param fn transformer for the original input string
         * @return this builder
         */
        public Converter preprocess(UnaryOperator<String> fn) {
            this.preprocessor = fn;
            return this;
        }

        /**
         * Replace the full options object.
         *
         * @param opts new options (non-null)
         * @return this builder
         */
        public Converter options(Options opts) {
            if (opts != null) this.options = opts;
            return this;
        }

        /**
         * Mutate options via a builder callback; seeded from current values.
         *
         * @param cfg mutating consumer for the options builder
         * @return this builder
         */
        public Converter options(java.util.function.Consumer<Options.Builder> cfg) {
            Options.Builder b = Options.builder();
            // seed from current
            b.normalizeSectionToAmpersand(options.normalizeSectionToAmpersand)
                    .transformHex(options.transformHex)
                    .transformStandardCodes(options.transformStandardCodes)
                    .legacyEmitResetOnClose(options.legacyEmitResetOnClose)
                    .legacyOutputHexColors(options.legacyOutputHexColors)
                    .xRepeatedHex(options.xRepeatedHexForLegacyOutput);
            if (cfg != null) cfg.accept(b);
            this.options = b.build();
            return this;
        }

        /**
         * Normalize the input to a MiniMessage string:
         * <ol>
         *   <li>(Optional) preprocess raw input</li>
         *   <li>Convert legacy hex + codes to MiniMessage (respecting {@link Options})</li>
         *   <li>Leave existing MiniMessage tags intact</li>
         * </ol>
         *
         * @return canonical MiniMessage string
         */
        public String toMiniMessage() {
            String s = originalInput == null ? "" : originalInput;
            if (preprocessor != null) s = preprocessor.apply(s);
            Set<InputFormat> exp = expects.isEmpty() ? InputFormat.MIXED_INPUT : expects;
            return normalizeToMiniMessage(s, exp, options);
        }

        /**
         * Convert the input to legacy codes using the specified legacy character.
         * <p>Internally normalizes to MiniMessage first, then serializes back to legacy.</p>
         * <p>Control hex style and scoping via {@link Options}.</p>
         *
         * @param legacyChar '&amp;' or '§'
         * @return legacy-formatted string
         */
        public String toLegacy(char legacyChar) {
            String mm = toMiniMessage(); // canonize first
            return miniMessageToLegacy(mm, legacyChar, options);
        }

        /**
         * Convert to plain text:
         * <ol>
         *   <li>Normalize to MiniMessage</li>
         *   <li>Strip legacy sequences</li>
         *   <li>Strip MiniMessage tags (keep text)</li>
         * </ol>
         *
         * @return plain text string
         */
        public String toPlain() {
            String mm = toMiniMessage();
            String noLegacy = stripLegacyCodes(mm);
            return stripMiniMessageTags(noLegacy);
        }
    }

    /* ========================= Engine: MiniMessage as canon ========================= */

    private static String normalizeToMiniMessage(String input, Set<InputFormat> expects, Options opt) {
        if (input == null || input.isEmpty()) return input;
        Objects.requireNonNull(expects, "expects");
        Objects.requireNonNull(opt, "options");

        String s = input;

        // Fast-path: only MiniMessage expected and no obvious legacy tokens
        if (expects.size() == 1 && expects.contains(InputFormat.MINIMESSAGE) && !likelyHasLegacy(s)) {
            return s;
        }

        // 1) Optional § → & normalization
        final boolean mayContainSection = expects.contains(InputFormat.LEGACY_SECTION)
                || expects.contains(InputFormat.HEX_BUNGEE_SECTION)
                || expects.contains(InputFormat.HEX_POUND);
        if (opt.normalizeSectionToAmpersand && mayContainSection && s.indexOf(FormatConstants.SECTION_CHAR) >= 0) {
            s = s.replace(FormatConstants.SECTION_CHAR, FormatConstants.AMP_CHAR);
        }

        // 2) Hex variants → <#RRGGBB>
        if (opt.transformHex) {
            if (expects.contains(InputFormat.HEX_POUND) && (indexOfAny(s, FormatConstants.AMP_CHAR, FormatConstants.SECTION_CHAR, FormatConstants.POUND_CHAR) >= 0)) {
                if (!opt.normalizeSectionToAmpersand) {
                    s = TextPatterns.SECTION_POUND_HEX.matcher(s).replaceAll("<#$1>");
                }
                s = TextPatterns.POUND_HEX.matcher(s).replaceAll("<#$1>");
            }
            if (expects.contains(InputFormat.HEX_BUNGEE_AMP) && s.indexOf(FormatConstants.AMP_CHAR) >= 0) {
                s = replaceAmpersandBungeeHexToMini(s);
            }
            if (expects.contains(InputFormat.HEX_BUNGEE_SECTION) && !opt.normalizeSectionToAmpersand && s.indexOf(FormatConstants.SECTION_CHAR) >= 0) {
                s = replaceSectionBungeeHexToMini(s);
            }
            if (expects.contains(InputFormat.HEX_MINI_DOUBLE) && TextPatterns.MINI_HEX_DOUBLE.matcher(s).find()) {
                s = TextPatterns.MINI_HEX_DOUBLE.matcher(s).replaceAll("<#$1>");
            }
        }

        // 3) Standard legacy codes → MiniMessage tags
        if (opt.transformStandardCodes) {
            if (expects.contains(InputFormat.LEGACY_AMPERSAND) && s.indexOf(FormatConstants.AMP_CHAR) >= 0) {
                s = LegacyColorUtils.convertAmpCodesToMini(s);
            }
            if (expects.contains(InputFormat.LEGACY_SECTION) && !opt.normalizeSectionToAmpersand && s.indexOf(FormatConstants.SECTION_CHAR) >= 0) {
                s = LegacyColorUtils.convertSecCodesToMini(s);
            }
        }

        // 4) Existing MiniMessage tags are respected
        return s;
    }

    private static boolean likelyHasLegacy(String s) {
        return (s.indexOf(FormatConstants.AMP_CHAR) >= 0 || s.indexOf(FormatConstants.SECTION_CHAR) >= 0 || s.indexOf(FormatConstants.POUND_CHAR) >= 0);
    }

    private static int indexOfAny(String s, char a, char b, char c) {
        int i = s.indexOf(a);
        if (i >= 0) return i;
        i = s.indexOf(b);
        if (i >= 0) return i;
        return s.indexOf(c);
    }

    /**
     * Convert {@code &x&F&F&F&F&F&F} to {@code <#RRGGBB>}.
     */
    private static String replaceAmpersandBungeeHexToMini(String s) {
        Matcher m = TextPatterns.AMP_BUNGEE_HEX.matcher(s);
        StringBuilder out = new StringBuilder(s.length());
        while (m.find()) {
            String seq = m.group(); // &x&F&F&F&F&F&F
            String hex = seq.replaceAll("(?i)&x|&", "");
            if (hex.length() >= 6) hex = hex.substring(0, 6);
            m.appendReplacement(out, "<#" + hex + ">");
        }
        m.appendTail(out);
        return out.toString();
    }

    /**
     * Convert {@code §x§F§F§F§F§F§F} to {@code <#RRGGBB>}.
     */
    private static String replaceSectionBungeeHexToMini(String s) {
        Matcher m = TextPatterns.SEC_BUNGEE_HEX.matcher(s);
        StringBuilder out = new StringBuilder(s.length());
        while (m.find()) {
            String seq = m.group(); // §x§F§F§F§F§F§F
            String hex = seq.replaceAll("(?i)§x|§", "");
            if (hex.length() >= 6) hex = hex.substring(0, 6);
            m.appendReplacement(out, "<#" + hex + ">");
        }
        m.appendTail(out);
        return out.toString();
    }

    /**
     * Serializer a MiniMessage string to legacy using the requested legacy character.
     * <p>Controls hex style and scope resets via {@link Options}.</p>
     */
    private static String miniMessageToLegacy(String mm, char legacyChar, Options opt) {
        if (mm == null || mm.isEmpty()) return mm;

        String out = mm;

        // 1) Normalize odd Mini hex <##RRGGBB> → <#RRGGBB> (just in case)
        if (out.indexOf(FormatConstants.MINI_TAG_OPEN) >= 0 && TextPatterns.MINI_HEX_DOUBLE.matcher(out).find()) {
            out = TextPatterns.MINI_HEX_DOUBLE.matcher(out).replaceAll("<#$1>");
        }

        // 2) Hex <#RRGGBB>
        if (out.indexOf(FormatConstants.POUND_CHAR) >= 0 && TextPatterns.MINI_HEX_TAG.matcher(out).find()) {
            Matcher hx = TextPatterns.MINI_HEX_TAG.matcher(out);
            StringBuilder sb = new StringBuilder(out.length());
            while (hx.find()) {
                String hex = hx.group(1);
                String replacement;
                if (opt.legacyOutputHexColors) {
                    replacement = opt.xRepeatedHexForLegacyOutput
                            ? xRepeatedFromHex(hex, legacyChar)
                            : legacyChar + "#" + hex.toUpperCase();
                } else {
                    char code = LegacyColorUtils.nearestNamedLegacyCode(hex);
                    replacement = "" + legacyChar + code;
                }
                hx.appendReplacement(sb, Matcher.quoteReplacement(replacement));
            }
            hx.appendTail(sb);
            out = sb.toString();
        }

        // 3) Named tags → legacy code
        for (var e : LegacyColorUtils.TAG_TO_CODE.entrySet()) {
            String tag = e.getKey();
            char code = e.getValue();
            out = out.replaceAll("(?i)<" + Pattern.quote(tag) + ">", Matcher.quoteReplacement("" + legacyChar + code));
        }

        // 4) Closing tags: convert to reset (&r/§r) if allowed (best-effort)
        if (opt.legacyEmitResetOnClose && out.indexOf(FormatConstants.MINI_TAG_OPEN) >= 0 && out.contains("</")) {
            out = out.replaceAll("(?i)</[a-z_]+>", Matcher.quoteReplacement("" + legacyChar + 'r'));
        }

        // Complex tags (gradient/click/hover) are preserved as-is by design.
        return out;
    }

    /**
     * Render hex as {@code &x&F&F&F&F&F&F}.
     */
    private static String xRepeatedFromHex(String hex, char legacyChar) {
        String up = hex.toUpperCase();
        String sep = String.valueOf(legacyChar);
        return legacyChar + "x" + sep + up.charAt(0) + sep + up.charAt(1)
                + sep + up.charAt(2) + sep + up.charAt(3) + sep + up.charAt(4) + sep + up.charAt(5);
    }

    /* ========================= Strippers / utilities ========================= */

    /**
     * Remove legacy codes (&amp;/§ + hex variants) from a string.
     * <p>Useful after normalization if you need plain text but the input may still contain legacy.</p>
     *
     * @param input input string
     * @return string without legacy codes
     */
    public static String stripLegacyCodes(String input) {
        if (input == null || input.isEmpty()) return input;
        String s = input;
        if (s.indexOf(FormatConstants.AMP_CHAR) >= 0) s = TextPatterns.AMP_BUNGEE_HEX.matcher(s).replaceAll("");
        if (s.indexOf(FormatConstants.SECTION_CHAR) >= 0) s = TextPatterns.SEC_BUNGEE_HEX.matcher(s).replaceAll("");
        if (s.indexOf(FormatConstants.POUND_CHAR) >= 0) {
            s = TextPatterns.POUND_HEX.matcher(s).replaceAll("");
            s = TextPatterns.SECTION_POUND_HEX.matcher(s).replaceAll("");
        }
        if (s.indexOf(FormatConstants.AMP_CHAR) >= 0) s = TextPatterns.AMP_CODES.matcher(s).replaceAll("");
        if (s.indexOf(FormatConstants.SECTION_CHAR) >= 0) s = TextPatterns.SEC_CODES.matcher(s).replaceAll("");
        return s;
    }

    /**
     * Strip all MiniMessage tags (keep inner text).
     * <p>Useful to produce plain text after {@link #toMiniMessage(String)} if you don't want formatting.</p>
     *
     * @param input input string
     * @return string without MiniMessage tags
     */
    public static String stripMiniMessageTags(String input) {
        if (input == null || input.isEmpty()) return input;
        String s = input;
        // Remove hex tags first
        if (s.indexOf(FormatConstants.POUND_CHAR) >= 0) s = TextPatterns.MINI_HEX_TAG.matcher(s).replaceAll("");
        // Remove any <tag ...> or </tag>
        if (s.indexOf(FormatConstants.MINI_TAG_OPEN) >= 0) s = TextPatterns.ANY_MINI_TAG.matcher(s).replaceAll("");
        return s;
    }

    /**
     * Escape user-provided text so MiniMessage will not interpret tags.
     * <p>Call this before injecting user content into a MiniMessage template.</p>
     *
     * @param raw user-provided text
     * @return escaped text (or null if input was null)
     */
    public static String escapeForMiniMessage(String raw) {
        return raw == null ? null : MiniMessage.miniMessage().escapeTags(raw);
    }
}
