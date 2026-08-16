package nl.hauntedmc.featureframework.localization;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import nl.hauntedmc.featureframework.toolkit.io.localization.Language;
import nl.hauntedmc.featureframework.toolkit.io.localization.MessageMap;
import nl.hauntedmc.featureframework.toolkit.log.FrameworkLogger;
import nl.hauntedmc.featureframework.toolkit.text.format.ComponentFormatter;
import nl.hauntedmc.featureframework.toolkit.text.format.TextFormatter;
import nl.hauntedmc.featureframework.toolkit.text.placeholder.MessagePlaceholders;
import nl.hauntedmc.featureframework.theme.ThemeRegistry;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Platform-neutral component rendering and fluent message construction around a
 * {@link LocalizationStore}. Platform bindings only supply player recognition, language lookup,
 * and optional platform placeholder expansion.
 *
 */
public class ComponentLocalization implements FeatureLocalization {
    private final LocalizationStore store;
    private final FrameworkLogger logger;
    private final Class<? extends Audience> playerType;
    private final Function<Audience, Language> playerLanguageResolver;
    private final BiFunction<String, Audience, String> platformPlaceholders;
    private final boolean preserveUnknownTags;
    private final ThemeRegistry themes;
    private final ThemeTagExpander themeTagExpander;
    private final ConcurrentMap<StaticMessageSlot, CachedStaticMessage> staticPlayerMessages =
            new ConcurrentHashMap<>();

    protected ComponentLocalization(
            LocalizationStore store,
            FrameworkLogger logger,
            Class<? extends Audience> playerType,
            Function<Audience, Language> playerLanguageResolver,
            BiFunction<String, Audience, String> platformPlaceholders,
            boolean preserveUnknownTags
    ) {
        this(store, logger, playerType, playerLanguageResolver, platformPlaceholders,
                preserveUnknownTags, ThemeRegistry.empty());
    }

    protected ComponentLocalization(
            LocalizationStore store,
            FrameworkLogger logger,
            Class<? extends Audience> playerType,
            Function<Audience, Language> playerLanguageResolver,
            BiFunction<String, Audience, String> platformPlaceholders,
            boolean preserveUnknownTags,
            ThemeRegistry themes
    ) {
        this.store = Objects.requireNonNull(store, "store");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.playerType = Objects.requireNonNull(playerType, "playerType");
        this.playerLanguageResolver = Objects.requireNonNull(playerLanguageResolver, "playerLanguageResolver");
        this.platformPlaceholders = Objects.requireNonNull(platformPlaceholders, "platformPlaceholders");
        this.preserveUnknownTags = preserveUnknownTags;
        this.themes = Objects.requireNonNull(themes, "themes");
        this.themeTagExpander = new ThemeTagExpander(themes,
                warning -> logger.warn("[FeatureFramework] " + warning));
    }

    protected final LocalizationStore store() {
        return store;
    }

    protected final FrameworkLogger logger() {
        return logger;
    }

    public final ThemeRegistry themes() {
        return themes;
    }

    @Override
    public void reloadLocalization() {
        staticPlayerMessages.clear();
        store.reload();
        logger.info(store.isFramework()
                ? "Framework localization files reloaded."
                : "Localization files reloaded for feature '" + store.featureName() + "'.");
    }

    @Override
    public void registerDefaultMessages(MessageMap messages) {
        if (store.registerDefaults(messages)) {
            staticPlayerMessages.clear();
            logger.info(store.isFramework()
                    ? "Registered missing framework localization defaults."
                    : "Registered missing localization defaults for feature '" + store.featureName() + "'.");
        }
    }

    public MessageBuilder getMessage(String key) {
        return new MessageBuilder(key);
    }

    public PlayerMessages messagesFor(Audience player) {
        Objects.requireNonNull(player, "player");
        return new PlayerMessages(player, resolvePlayerLanguage(player));
    }

    public final class PlayerMessages {
        private final Audience player;
        private final Language language;

        private PlayerMessages(Audience player, Language language) {
            this.player = player;
            this.language = language;
        }

        public Component build(String key) {
            Objects.requireNonNull(key, "key");
            String raw = store.message(key, language);
            if (raw.indexOf('%') >= 0) {
                return render(raw, player, player, MessagePlaceholders.empty(), false, true);
            }
            StaticMessageSlot slot = new StaticMessageSlot(language, key);
            return staticPlayerMessages.compute(slot, (ignored, cached) -> {
                if (cached != null && cached.raw().equals(raw)) {
                    return cached;
                }
                return new CachedStaticMessage(
                        raw,
                        render(raw, null, null, MessagePlaceholders.empty(), false, true)
                );
            }).component();
        }
    }

    public class MessageBuilder {
        private final String key;
        private Audience audience;
        private Audience placeholderPlayer;
        private MessagePlaceholders placeholders = MessagePlaceholders.empty();
        private boolean autoLinkUrls;
        private boolean autoLinkUnderline = true;

        protected MessageBuilder(String key) {
            this.key = Objects.requireNonNull(key, "key");
        }

        public MessageBuilder forAudience(Audience value) {
            audience = value;
            return this;
        }

        public MessageBuilder withPlaceholderPlayer(Audience player) {
            placeholderPlayer = player;
            return this;
        }

        public MessageBuilder withPlaceholders(MessagePlaceholders value) {
            if (value != null) {
                placeholders = value;
            }
            return this;
        }

        public MessageBuilder with(String name, String value) {
            placeholders = MessagePlaceholders.builder().addAll(placeholders).addString(name, value).build();
            return this;
        }

        public MessageBuilder with(String name, Number value) {
            placeholders = MessagePlaceholders.builder().addAll(placeholders).addNumber(name, value).build();
            return this;
        }

        public MessageBuilder with(String name, boolean value) {
            return with(name, Boolean.toString(value));
        }

        public MessageBuilder with(String name, Component value) {
            placeholders = MessagePlaceholders.builder().addAll(placeholders).addComponent(name, value).build();
            return this;
        }

        public MessageBuilder autoLinkUrls(boolean enabled) {
            autoLinkUrls = enabled;
            return this;
        }

        public MessageBuilder autoLinkUnderline(boolean enabled) {
            autoLinkUnderline = enabled;
            return this;
        }

        public Component build() {
            Audience audiencePlayer = playerType.isInstance(audience) ? audience : null;
            Language language = audiencePlayer == null ? null : resolvePlayerLanguage(audiencePlayer);
            String raw = store.message(key, language);
            return render(raw, audience, placeholderPlayer, placeholders, autoLinkUrls, autoLinkUnderline);
        }
    }

    private Language resolvePlayerLanguage(Audience player) {
        Language language = playerLanguageResolver.apply(player);
        return language == null ? Language.NL : language;
    }

    private Component render(
            String raw,
            Audience audience,
            Audience explicitPlaceholderPlayer,
            MessagePlaceholders placeholders,
            boolean autoLinkUrls,
            boolean autoLinkUnderline
    ) {
        String message = TextFormatter.convert(raw)
                .expect(TextFormatter.InputFormat.MIXED_INPUT)
                .preprocess(text -> {
                    Audience contextPlayer = explicitPlaceholderPlayer;
                    if (contextPlayer == null && playerType.isInstance(audience)) {
                        contextPlayer = audience;
                    }
                    String expanded = contextPlayer == null
                            ? text
                            : platformPlaceholders.apply(text, contextPlayer);
                    return MessagePlaceholders.applyPlaceholders(expanded, placeholders);
                })
                .toMiniMessage();
        message = themeTagExpander.expand(message);

        ComponentFormatter.Converter converter = ComponentFormatter.deserialize(message)
                .sanitizeUnknownTags(!preserveUnknownTags)
                .expect(TextFormatter.InputFormat.MINIMESSAGE)
                .features(ComponentFormatter.ALL_DEFAULTS());
        themes.themes().forEach(theme -> converter.allowTagName(theme.id().value()));
        if (autoLinkUrls) {
            converter.autoLinkUrls(autoLinkUnderline);
        }
        return converter.toComponent();
    }

    private record StaticMessageSlot(Language language, String key) {
    }

    private record CachedStaticMessage(String raw, Component component) {
    }
}
