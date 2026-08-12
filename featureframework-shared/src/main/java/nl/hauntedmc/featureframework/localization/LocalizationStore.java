package nl.hauntedmc.featureframework.localization;

import nl.hauntedmc.featureframework.config.ConfigDefaultsMerger;
import nl.hauntedmc.featureframework.config.FeatureStoragePaths;
import nl.hauntedmc.featureframework.toolkit.io.config.ConfigNode;
import nl.hauntedmc.featureframework.toolkit.io.config.ConfigService;
import nl.hauntedmc.featureframework.toolkit.io.config.ConfigView;
import nl.hauntedmc.featureframework.toolkit.io.localization.Language;
import nl.hauntedmc.featureframework.toolkit.io.localization.MessageMap;
import nl.hauntedmc.featureframework.toolkit.log.FrameworkLogger;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.yaml.NodeStyle;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Platform-neutral localization storage, fallback, defaults, and reload behavior. */
public final class LocalizationStore implements FeatureLocalization {
    public static final String LANGUAGE_DIRECTORY = "lang";

    private final ClassLoader resources;
    private final ConfigService configService;
    private final FrameworkLogger logger;
    private final String featureName;
    private final LocalizationStore frameworkFallback;
    private final ConfigView defaultMessages;
    private final EnumMap<Language, ConfigView> translations = new EnumMap<>(Language.class);

    public LocalizationStore(ClassLoader resources, ConfigService configService, FrameworkLogger logger) {
        this(resources, configService, logger, null, null);
    }

    private LocalizationStore(
            ClassLoader resources,
            ConfigService configService,
            FrameworkLogger logger,
            String featureName,
            LocalizationStore frameworkFallback
    ) {
        this.resources = Objects.requireNonNull(resources, "resources");
        this.configService = Objects.requireNonNull(configService, "configService");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.featureName = featureName;
        this.frameworkFallback = frameworkFallback;
        this.defaultMessages = configService.view(defaultMessagesPath(), featureName == null);
        reload();
    }

    public LocalizationStore openFeature(String requestedFeatureName) {
        if (featureName != null) return frameworkFallback.openFeature(requestedFeatureName);
        return new LocalizationStore(
                resources,
                configService,
                logger,
                FeatureStoragePaths.normalizeFeatureName(requestedFeatureName),
                this
        );
    }

    public boolean isFramework() { return featureName == null; }
    public String featureName() { return featureName; }

    public synchronized void reload() {
        if (isFramework()) registerBundledDefaults();
        defaultMessages.file.reload();
        translations.clear();
        for (Language language : Language.localizableValues()) {
            String path = languagePath(language);
            if (isFramework()) {
                ConfigView view = configService.view(path, true);
                view.file.reload();
                translations.put(language, view);
            } else {
                configService.openExisting(path).ifPresent(file -> {
                    file.reload();
                    translations.put(language, new ConfigView(file, ""));
                });
            }
        }
    }

    public synchronized boolean registerDefaults(MessageMap messages) {
        if (messages == null || messages.getMessages().isEmpty()) return false;
        Map<String, String> missing = new LinkedHashMap<>();
        messages.getMessages().forEach((key, value) -> {
            if (defaultMessages.node(key).isNull()) missing.put(key, value);
        });
        if (missing.isEmpty()) return false;
        defaultMessages.batch(batch -> missing.forEach(batch::put));
        return true;
    }

    @Override
    public void registerDefaultMessages(MessageMap messages) {
        registerDefaults(messages);
    }

    @Override
    public void reloadLocalization() {
        reload();
    }

    public String message(String key, Language language) {
        Objects.requireNonNull(key, "key");
        String value = findMessage(key, language);
        return value == null ? missingMessage(key) : value;
    }

    private String findMessage(String key, Language language) {
        if (language != null) {
            ConfigView translated = translations.get(language);
            if (translated != null) {
                String value = translated.get(key, String.class);
                if (value != null) return value;
            }
        }
        String value = defaultMessages.get(key, String.class);
        if (value != null) return value;
        return frameworkFallback == null ? null : frameworkFallback.findMessage(key, language);
    }

    private String missingMessage(String key) {
        return frameworkFallback == null ? "&cMessage not found: " + key : frameworkFallback.missingMessage(key);
    }

    private void registerBundledDefaults() {
        mergeBundledDefaults(defaultMessages, defaultMessagesPath());
        for (Language language : Language.localizableValues()) {
            String path = languagePath(language);
            mergeBundledDefaults(configService.view(path, true), path);
        }
    }

    private void mergeBundledDefaults(ConfigView target, String resourcePath) {
        ConfigNode bundled = loadBundledResource(resourcePath);
        if (bundled == null || bundled.isNull()) return;
        Map<String, Object> defaults = new LinkedHashMap<>();
        bundled.children().forEach((key, node) -> defaults.put(key, node.raw()));
        ConfigDefaultsMerger.mergeMissingPaths(target, defaults);
    }

    private ConfigNode loadBundledResource(String resourcePath) {
        try (InputStream input = resources.getResourceAsStream(resourcePath)) {
            if (input == null) return null;
            String yaml = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            CommentedConfigurationNode root = YamlConfigurationLoader.builder()
                    .nodeStyle(NodeStyle.BLOCK)
                    .source(() -> new BufferedReader(new StringReader(yaml)))
                    .build().load();
            return ConfigNode.ofRaw(root.get(Object.class), resourcePath);
        } catch (Exception failure) {
            logger.warn("Could not load bundled localization resource '" + resourcePath + "'.", failure);
            return null;
        }
    }

    private String defaultMessagesPath() {
        return isFramework() ? LANGUAGE_DIRECTORY + "/messages.yml" : FeatureStoragePaths.messagesPath(featureName);
    }

    private String languagePath(Language language) {
        return isFramework()
                ? LANGUAGE_DIRECTORY + "/" + language.getFileName()
                : FeatureStoragePaths.messagesPath(featureName, language);
    }
}
