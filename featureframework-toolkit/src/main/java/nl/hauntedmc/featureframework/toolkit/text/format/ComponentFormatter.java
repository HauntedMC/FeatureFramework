package nl.hauntedmc.featureframework.toolkit.text.format;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import nl.hauntedmc.featureframework.toolkit.text.TextPatterns;
import nl.hauntedmc.featureframework.toolkit.text.format.constants.FormatConstants;

import java.util.*;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;

/**
 * {@code ComponentCodec} — one-stop utility to convert <b>strings</b> into Adventure {@link Component}s.
 *
 * <h2>What it does</h2>
 * <ul>
 *   <li>Accepts <em>mixed</em> input: legacy (&amp;/§ and hex) and/or MiniMessage and/or plain text.</li>
 *   <li>Normalizes the input to <b>MiniMessage</b> using {@link TextFormatter}, then parses to a {@link Component}.</li>
 *   <li>Lets you <b>whitelist exactly which MiniMessage features</b> are allowed during parsing.</li>
 *   <li>Optionally <b>sanitizes</b> disallowed tags (keeps text, strips markup) before strict parsing.</li>
 *   <li>Optionally <b>auto-links naked URLs</b> with standard {@code <click:open_url:...>} tags.</li>
 *   <li>Optionally <b>registers custom tags</b> (plus allowlist for the sanitizer).</li>
 * </ul>
 *
 * <h2>When to use</h2>
 * <ul>
 *   <li><b>Chat input</b>: allow just colors, or colors+decorations, or fully interactive formatting.</li>
 *   <li><b>Server messages</b>: enable all standard tags, no sanitization (trusted templates).</li>
 *   <li><b>Untrusted user input</b>: enable only a safe subset and keep {@link Converter#sanitizeUnknownTags(boolean)} true.</li>
 * </ul>
 *
 * <h2>Typical examples</h2>
 * <pre>{@code
 * // Unranked chat: plain text with clickable links and newlines only
 * Component c = ComponentCodec.convert(raw)
 *     .expect(TextCodec.Input.ANY)
 *     .features(ComponentCodec.Feature.NEWLINE, ComponentCodec.Feature.CLICK)
 *     .autoLinkUrls()                // converts naked URLs to <click:open_url:...>
 *     .toComponent();
 *
 * // Ranked chat: colors + decorations + gradients + links
 * Component c = ComponentCodec.convert(raw)
 *     .expect(TextCodec.Input.ANY_LEGACY)
 *     .features(
 *         ComponentCodec.Feature.COLORS, ComponentCodec.Feature.DECORATIONS,
 *         ComponentCodec.Feature.GRADIENT, ComponentCodec.Feature.RAINBOW,
 *         ComponentCodec.Feature.CLICK, ComponentCodec.Feature.HOVER,
 *         ComponentCodec.Feature.RESET, ComponentCodec.Feature.NEWLINE
 *     )
 *     .autoLinkUrls()
 *     .toComponent();
 *
 * // Server messages (trusted templates): allow everything; no need to sanitize
 * Component c = ComponentCodec.convert(miniMsgTemplate)
 *     .expect(TextCodec.Input.MINIMESSAGE)
 *     .features(ComponentCodec.ALL_DEFAULTS())
 *     .sanitizeUnknownTags(false)
 *     .toComponent();
 * }</pre>
 *
 * <p><b>Thread-safety:</b> The class is stateless; each {@link Converter} is single-use and not thread-safe.</p>
 */
public final class ComponentFormatter {
    private ComponentFormatter() {
    }

    /**
     * Start a new conversion pipeline for {@code input}.
     *
     * @param input raw input string (may contain legacy, MiniMessage, or plain text)
     * @return a fluent {@link Converter}
     */
    public static Converter deserialize(String input) {
        return new Converter(input);
    }


    /**
     * Serializer a {@link Component} to a string in the chosen {@link Serializer.Format}.
     */
    public static Serializer.Builder serialize(Component component) {
        return new Serializer.Builder(component);
    }

    /**
     * Convenience macro approximating {@link StandardTags#defaults()}.
     * <p>Use this for trusted server-side messages where all standard tags are allowed.</p>
     *
     * @return a set containing all default features
     */
    public static Set<Feature> ALL_DEFAULTS() {
        return EnumSet.of(
                Feature.COLORS, Feature.DECORATIONS, Feature.CLICK, Feature.HOVER, Feature.RESET,
                Feature.GRADIENT, Feature.RAINBOW, Feature.NEWLINE, Feature.TRANSITION,
                Feature.KEYBIND, Feature.TRANSLATABLE, Feature.TRANSLATABLE_FALLBACK,
                Feature.INSERTION, Feature.FONT, Feature.SELECTOR, Feature.SCORE, Feature.NBT,
                Feature.PRIDE, Feature.SHADOW_COLOR
        );
    }

    /**
     * Build a MiniMessage parser with only the requested resolvers and strict mode.
     */
    private static MiniMessage buildMiniMessage(Set<Feature> features,
                                                Set<TagResolver> extras,
                                                boolean strict) {
        TagResolver.Builder tr = TagResolver.builder();
        if (features.contains(Feature.COLORS)) tr.resolver(StandardTags.color());
        if (features.contains(Feature.DECORATIONS)) tr.resolver(StandardTags.decorations());
        if (features.contains(Feature.CLICK)) tr.resolver(StandardTags.clickEvent());
        if (features.contains(Feature.HOVER)) tr.resolver(StandardTags.hoverEvent());
        if (features.contains(Feature.RESET)) tr.resolver(StandardTags.reset());
        if (features.contains(Feature.GRADIENT)) tr.resolver(StandardTags.gradient());
        if (features.contains(Feature.RAINBOW)) tr.resolver(StandardTags.rainbow());
        if (features.contains(Feature.NEWLINE)) tr.resolver(StandardTags.newline());
        if (features.contains(Feature.TRANSITION)) tr.resolver(StandardTags.transition());
        if (features.contains(Feature.SHADOW_COLOR)) tr.resolver(StandardTags.shadowColor());
        if (features.contains(Feature.PRIDE)) tr.resolver(StandardTags.pride());
        if (features.contains(Feature.KEYBIND)) tr.resolver(StandardTags.keybind());
        if (features.contains(Feature.TRANSLATABLE)) tr.resolver(StandardTags.translatable());
        if (features.contains(Feature.TRANSLATABLE_FALLBACK)) tr.resolver(StandardTags.translatableFallback());
        if (features.contains(Feature.INSERTION)) tr.resolver(StandardTags.insertion());
        if (features.contains(Feature.FONT)) tr.resolver(StandardTags.font());
        if (features.contains(Feature.SELECTOR)) tr.resolver(StandardTags.selector());
        if (features.contains(Feature.SCORE)) tr.resolver(StandardTags.score());
        if (features.contains(Feature.NBT)) tr.resolver(StandardTags.nbt());
        for (TagResolver r : extras) tr.resolver(r);

        return MiniMessage.builder()
                .tags(tr.build())
                .strict(strict)
                .build();
    }

    /**
     * Whitelist of MiniMessage features to enable during parsing.
     * <p>Anything not enabled can be sanitized away (if {@link Converter#sanitizeUnknownTags(boolean)} is true).</p>
     */
    public enum Feature {
        /**
         * Named colors and hex, e.g. {@code <red>}, {@code <#FF00FF>}, {@code <color:#FF00FF>}.
         */
        COLORS,
        /**
         * Decorations like {@code <bold>}, {@code <italic>}, {@code <underlined>}, {@code <strikethrough>}, {@code <obfuscated>}.
         */
        DECORATIONS,
        /**
         * {@code <gradient:...> ... </gradient>}.
         */
        GRADIENT,
        /**
         * {@code <rainbow> ... </rainbow>}.
         */
        RAINBOW,
        /**
         * Click events, e.g. {@code <click:run_command:/say hi>}.
         */
        CLICK,
        /**
         * Hover events, e.g. {@code <hover:show_text:'<red>Hello!'>}}.
         */
        HOVER,
        /**
         * {@code <reset>}.
         */
        RESET,
        /**
         * {@code <newline>} and {@code <br>}.
         */
        NEWLINE,
        /**
         * {@code <transition>}.
         */
        TRANSITION,
        /**
         * Shadow color tags (adventure-extra): {@code <shadow:#xxxxxx>}.
         */
        SHADOW_COLOR,
        /**
         * Pride gradients: {@code <pride:...>}.
         */
        PRIDE,
        /**
         * {@code <keybind:key.name>}.
         */
        KEYBIND,
        /**
         * Translatable: {@code <translate:key>}, {@code <tr:key>}, {@code <lang:key>}.
         */
        TRANSLATABLE,
        /**
         * Translatable with fallback: {@code <translate_or:key|text>}, {@code <tr_or:...>}, {@code <lang_or:...>}.
         */
        TRANSLATABLE_FALLBACK,
        /**
         * {@code <insertion:text>}.
         */
        INSERTION,
        /**
         * {@code <font:resource>}.
         */
        FONT,
        /**
         * Entity selector: {@code <selector:...>} / {@code <sel:...>}.
         */
        SELECTOR,
        /**
         * Score: {@code <score:...>}.
         */
        SCORE,
        /**
         * NBT/data: {@code <nbt:...>} / {@code <data:...>}.
         */
        NBT
    }

    /**
     * Fluent builder that normalizes the input to MiniMessage (via {@link TextFormatter}) and then
     * parses it into a {@link Component} using only the whitelisted features.
     */
    public static final class Converter {
        private final String originalInput;
        private final EnumSet<TextFormatter.InputFormat> expects = EnumSet.noneOf(TextFormatter.InputFormat.class);
        private final EnumSet<Feature> features = EnumSet.noneOf(Feature.class);
        private final Set<TagResolver> extraResolvers = new LinkedHashSet<>();
        private final Set<String> allowedCustomTagNames = new HashSet<>();
        private boolean sanitizeUnknownTags = true;
        private boolean strict = false;
        private UnaryOperator<String> preprocessor;
        private boolean autoLinkUrls = false;
        private boolean autoLinkUnderline = true;

        private Converter(String input) {
            this.originalInput = input == null ? "" : input;
        }

        /**
         * Declare expected input formats (multiple allowed). If not set, defaults to
         * {@link TextFormatter.InputFormat#MIXED_INPUT}.
         *
         * @param inputs one or more expected formats
         * @return this builder
         */
        public Converter expect(TextFormatter.InputFormat... inputs) {
            if (inputs != null) for (var in : inputs) if (in != null) expects.add(in);
            return this;
        }

        /**
         * Declare expected input formats from a prebuilt set.
         *
         * @param inputs set of expected formats
         * @return this builder
         */
        public Converter expect(Set<TextFormatter.InputFormat> inputs) {
            if (inputs != null) expects.addAll(inputs);
            return this;
        }

        /**
         * Enable specific MiniMessage features to be active during parsing.
         * Anything not enabled can be stripped by the sanitizer.
         *
         * @param fs features to enable
         * @return this builder
         */
        public Converter features(Feature... fs) {
            if (fs != null) for (var f : fs) if (f != null) features.add(f);
            return this;
        }

        /**
         * Enable a prebuilt feature set.
         *
         * @param fs set of features (e.g. {@link #ALL_DEFAULTS()})
         * @return this builder
         */
        public Converter features(Set<Feature> fs) {
            if (fs != null) features.addAll(fs);
            return this;
        }

        /**
         * If true (default), unknown/disallowed tags are stripped <em>before</em> strict parsing,
         * preserving inner text. Keep this on for untrusted input (chat).
         *
         * @param on true to strip disallowed tags, false to leave them and rely on MiniMessage behavior
         * @return this builder
         */
        public Converter sanitizeUnknownTags(boolean on) {
            this.sanitizeUnknownTags = on;
            return this;
        }

        /**
         * Enable/disable strict MiniMessage parsing (default: false).
         * With {@code strict=true}, any remaining unknown tags cause parse errors.
         *
         * @param on true to enable strict parsing
         * @return this builder
         */
        public Converter strict(boolean on) {
            this.strict = on;
            return this;
        }

        /**
         * Optional preprocessor (e.g., PlaceholderAPI) applied to the raw input <em>before</em> normalization.
         *
         * @param fn transformer applied to the original input string
         * @return this builder
         */
        public Converter preprocess(UnaryOperator<String> fn) {
            this.preprocessor = fn;
            return this;
        }

        /**
         * Register a custom MiniMessage tag resolver and allow its tag name through the sanitizer.
         * Use this for custom integrations (not needed for standard click/hover).
         *
         * @param name     lower-case tag name to allow (e.g., {@code "mytag"})
         * @param resolver resolver to handle the tag
         * @return this builder
         */
        public Converter withCustomTag(String name, TagResolver resolver) {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(resolver, "resolver");
            this.extraResolvers.add(resolver);
            this.allowedCustomTagNames.add(name.toLowerCase());
            return this;
        }

        /**
         * Allows a tag name through sanitization without registering a resolver.
         * This is useful for escaped custom markup that must remain visible as literal text.
         */
        public Converter allowTagName(String name) {
            Objects.requireNonNull(name, "name");
            String normalized = name.trim().toLowerCase(Locale.ROOT);
            if (normalized.isEmpty()) throw new IllegalArgumentException("name must not be blank");
            this.allowedCustomTagNames.add(normalized);
            return this;
        }

        /**
         * Convert naked URLs ({@code http(s)://...} or {@code www....}) into
         * {@code <click:open_url:...>} regions. Has effect only if {@link Feature#CLICK} is enabled.
         *
         * @param underline if true and {@link Feature#DECORATIONS} is enabled, the visible URL is underlined
         * @return this builder
         */
        public Converter autoLinkUrls(boolean underline) {
            this.autoLinkUrls = true;
            this.autoLinkUnderline = underline;
            return this;
        }

        /**
         * Convenience overload: auto-link and underline (if {@link Feature#DECORATIONS} is enabled).
         *
         * @return this builder
         */
        public Converter autoLinkUrls() {
            return autoLinkUrls(true);
        }

        /**
         * Build the {@link Component}:
         * <ol>
         *   <li>Apply {@link #preprocess(UnaryOperator)} if present.</li>
         *   <li>Normalize to MiniMessage using {@link TextFormatter} and {@link #expect(TextFormatter.InputFormat...)}.</li>
         *   <li>Optionally wrap naked URLs in {@code <click:open_url:...>}.</li>
         *   <li>Optionally strip not-allowed tags (keep text).</li>
         *   <li>Parse to {@link Component} using only the whitelisted {@link Feature}s and {@code strict} mode.</li>
         * </ol>
         *
         * @return parsed {@link Component}
         */
        public Component toComponent() {
            String s = preprocessor != null ? preprocessor.apply(originalInput) : originalInput;

            // Normalize to MiniMessage via TextCodec (string-only)
            Set<TextFormatter.InputFormat> exp = expects.isEmpty() ? TextFormatter.InputFormat.MIXED_INPUT : expects;
            String mm = (exp.size() == 1 && exp.contains(TextFormatter.InputFormat.MINIMESSAGE))
                    ? s
                    : TextFormatter.convert(s).expect(exp).toMiniMessage();


            if (sanitizeUnknownTags) {
                mm = Sanitizer.stripDisallowed(mm, features, allowedCustomTagNames);
            }

            MiniMessage parser = buildMiniMessage(features, extraResolvers, strict);
            Component c = parser.deserialize(mm);

            if (autoLinkUrls) {
                c = AutoLinker.autoLink(c, autoLinkUnderline);
            }

            return c;
        }
    }

    /**
     * Internal sanitizer that removes tags <em>not</em> covered by the enabled {@link Feature}s
     * or explicitly allowed custom names. Inner text is preserved.
     * <p>Used only when {@link Converter#sanitizeUnknownTags(boolean)} is true.</p>
     */
    private static final class Sanitizer {

        private static final Map<String, Boolean> NAMED_COLOR = Map.ofEntries(
                e("black"), e("dark_blue"), e("dark_green"), e("dark_aqua"), e("dark_red"),
                e("dark_purple"), e("gold"), e("gray"), e("grey"), e("dark_gray"), e("dark_grey"),
                e("blue"), e("green"), e("aqua"), e("red"), e("light_purple"), e("purple"),
                e("yellow"), e("white")
        );

        private static Map.Entry<String, Boolean> e(String k) {
            return Map.entry(k, Boolean.TRUE);
        }

        /**
         * Strip tags not in the allowlist; keep inner text intact.
         */
        static String stripDisallowed(String mm, Set<Feature> features, Set<String> allowedCustom) {
            if (mm == null || mm.isEmpty()) return mm;
            String out = mm;

            if (!features.contains(Feature.COLORS) && out.indexOf(FormatConstants.POUND_CHAR) >= 0) {
                out = TextPatterns.HEX_TAG.matcher(out).replaceAll("");
            }

            var allowed = new HashSet<>(allowedCustom == null ? Set.of() : allowedCustom);

            if (features.contains(Feature.COLORS)) {
                allowed.add("color");
                allowed.addAll(NAMED_COLOR.keySet());
            }
            if (features.contains(Feature.DECORATIONS)) {
                allowed.add("bold");
                allowed.add("italic");
                allowed.add("underlined");
                allowed.add("underline");
                allowed.add("strikethrough");
                allowed.add("obfuscated");
            }
            if (features.contains(Feature.GRADIENT)) allowed.add("gradient");
            if (features.contains(Feature.RAINBOW)) allowed.add("rainbow");
            if (features.contains(Feature.CLICK)) allowed.add("click");
            if (features.contains(Feature.HOVER)) allowed.add("hover");
            if (features.contains(Feature.RESET)) allowed.add("reset");
            if (features.contains(Feature.NEWLINE)) {
                allowed.add("newline");
                allowed.add("br");
            }
            if (features.contains(Feature.TRANSITION)) allowed.add("transition");
            if (features.contains(Feature.SHADOW_COLOR)) {
                allowed.add("shadow");
                allowed.add("shadow_color");
            }
            if (features.contains(Feature.PRIDE)) allowed.add("pride");

            if (features.contains(Feature.KEYBIND)) allowed.add("keybind");
            if (features.contains(Feature.TRANSLATABLE)) {
                allowed.add("translate");
                allowed.add("tr");
                allowed.add("lang");
            }
            if (features.contains(Feature.TRANSLATABLE_FALLBACK)) {
                allowed.add("translate_or");
                allowed.add("tr_or");
                allowed.add("lang_or");
            }
            if (features.contains(Feature.INSERTION)) allowed.add("insertion");
            if (features.contains(Feature.FONT)) allowed.add("font");
            if (features.contains(Feature.SELECTOR)) {
                allowed.add("selector");
                allowed.add("sel");
            }
            if (features.contains(Feature.SCORE)) allowed.add("score");
            if (features.contains(Feature.NBT)) {
                allowed.add("nbt");
                allowed.add("data");
            }

            allowed.add(FormatConstants.END_TOKEN);

            if (!features.contains(Feature.NEWLINE) && out.indexOf('<') >= 0) {
                out = TextPatterns.NEWLINE_TAG.matcher(out).replaceAll("");
            }

            out = TextPatterns.OPEN_TAG.matcher(out).replaceAll(mr -> {
                String name = mr.group(1).toLowerCase();
                return allowed.contains(name) ? mr.group(0) : "";
            });
            out = TextPatterns.CLOSE_TAG.matcher(out).replaceAll(mr -> {
                String name = mr.group(1).toLowerCase();
                return allowed.contains(name) ? mr.group(0) : "";
            });

            return out;
        }
    }

    /**
     * Internal autolinker that wraps naked URLs in {@code <click:open_url:...>} (optionally underlined).
     * <p>Safe to run over MiniMessage text before parsing; avoids matching inside tag angle brackets.</p>
     */
    private static final class AutoLinker {

        /**
         * Convert {@code http(s)://...} or {@code www....} to clickable regions.
         */
        static String autoLink(String mm, boolean underline) {
            if (mm == null || mm.isEmpty()) return mm;
            Matcher m = TextPatterns.URL.matcher(mm);
            StringBuilder sb = new StringBuilder(mm.length());
            while (m.find()) {
                String url = m.group(1);
                String href = url.startsWith("www.") ? "https://" + url : url;
                String replacement = underline
                        ? "<click:open_url:" + href + "><underlined>" + url + "</underlined></click>"
                        : "<click:open_url:" + href + ">" + url + "</click>";
                m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
            }
            m.appendTail(sb);
            return sb.toString();
        }

        static Component autoLink(Component root, boolean underline) {
            if (root == null) return Component.empty();
            // Use your existing URL pattern (it’s already resistant to tags via your normalization)
            return root.replaceText(cfg -> cfg
                    .match(TextPatterns.URL)
                    .replacement((match, builder) -> {
                        String url = match.group(1);
                        String href = url.startsWith("www.") ? "https://" + url : url;

                        net.kyori.adventure.text.TextComponent tc = Component.text(url);
                        if (underline) {
                            tc = tc.decorate(net.kyori.adventure.text.format.TextDecoration.UNDERLINED);
                        }
                        return tc.clickEvent(net.kyori.adventure.text.event.ClickEvent.openUrl(href));
                    })
            );
        }
    }

    /**
     * <h2>Serializer</h2>
     * Helpers for converting Adventure {@link Component} ⇄ String across:
     * PLAIN, LEGACY ({@code &}/§), MINIMESSAGE, JSON, JSON_DOWNSAMPLED
     */
    public static final class Serializer {
        // Singletons
        private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
        private static final LegacyComponentSerializer LEGACY_AMP = LegacyComponentSerializer.legacyAmpersand();
        private static final LegacyComponentSerializer LEGACY_SEC = LegacyComponentSerializer.legacySection();
        private static final GsonComponentSerializer GSON = GsonComponentSerializer.gson();
        private static final GsonComponentSerializer GSON_DOWNSAMPLED = GsonComponentSerializer.colorDownsamplingGson();
        private static final MiniMessage MINIMESSAGE = MiniMessage.miniMessage();
        private static final Gson PRETTY_GSON = new GsonBuilder().setPrettyPrinting().create();

        private Serializer() {
        }

        private static LegacyComponentSerializer buildLegacy(LegacyOptions opts) {
            LegacyOptions o = (opts == null) ? LegacyOptions.ampersand() : opts;
            LegacyComponentSerializer.Builder b = LegacyComponentSerializer.builder()
                    .character(o.legacyChar)
                    .hexCharacter(o.legacyHexChar);
            if (o.supportHex) b.hexColors();
            if (o.useXRepeatedHex) b.useUnusualXRepeatedCharacterHexFormat();
            if (o.extractUrls) b.extractUrls();
            return b.build();
        }

        /**
         * Output format selector.
         */
        public enum Format {
            PLAIN,
            LEGACY_AMPERSAND,
            LEGACY_SECTION,
            MINIMESSAGE,
            JSON,
            JSON_DOWNSAMPLED
        }

        /**
         * Options for constructing a custom {@link LegacyComponentSerializer}.
         */
        public static final class LegacyOptions {
            public char legacyChar = FormatConstants.AMP_CHAR;
            public final char legacyHexChar = FormatConstants.POUND_CHAR;
            public final boolean supportHex = true;
            public final boolean useXRepeatedHex = false;
            public final boolean extractUrls = false;

            public static LegacyOptions ampersand() {
                return new LegacyOptions();
            }

            public static LegacyOptions section() {
                LegacyOptions o = new LegacyOptions();
                o.legacyChar = FormatConstants.SECTION_CHAR;
                return o;
            }
        }

        /**
         * Fluent builder for component → string serialization.
         */
        public static final class Builder {
            private final Component source;

            private Format format = Format.MINIMESSAGE;  // sensible default
            private LegacyOptions legacyOptions = null;
            private boolean jsonPretty = false;

            public Builder(Component source) {
                this.source = Objects.requireNonNull(source, "component");
            }

            /**
             * Select target format (defaults to {@link Format#MINIMESSAGE}).
             */
            public Builder format(Format format) {
                this.format = Objects.requireNonNull(format, "format");
                return this;
            }

            /**
             * Provide fully custom legacy options (character, hex, {@code &x&R&R...} hex,
             * URL extraction).
             */
            public Builder legacyOptions(LegacyOptions options) {
                this.legacyOptions = options;
                return this;
            }

            /**
             * Pretty-print JSON (ignored for non-JSON formats).
             */
            public Builder jsonPretty(boolean on) {
                this.jsonPretty = on;
                return this;
            }

            /**
             * Build the final string.
             */
            public String build() {
                // 1) apply transforms if requested
                Component c = source;

                // 2) serialize in chosen format
                return switch (format) {
                    case PLAIN -> PLAIN.serialize(c);

                    case LEGACY_AMPERSAND -> {
                        if (legacyOptions == null) {
                            yield LEGACY_AMP.serialize(c);
                        }
                        yield buildLegacy(legacyOptions).serialize(c);
                    }

                    case LEGACY_SECTION -> {
                        if (legacyOptions == null) {
                            yield LEGACY_SEC.serialize(c);
                        }
                        yield buildLegacy(legacyOptions).serialize(c);
                    }

                    case MINIMESSAGE -> MINIMESSAGE.serialize(c);

                    case JSON -> {
                        if (jsonPretty) {
                            JsonElement tree = GSON.serializeToTree(c);
                            yield PRETTY_GSON.toJson(tree);
                        }
                        yield GSON.serialize(c);
                    }

                    case JSON_DOWNSAMPLED -> {
                        if (jsonPretty) {
                            JsonElement tree = GSON_DOWNSAMPLED.serializeToTree(c);
                            yield PRETTY_GSON.toJson(tree);
                        }
                        yield GSON_DOWNSAMPLED.serialize(c);
                    }
                };
            }
        }
    }
}
