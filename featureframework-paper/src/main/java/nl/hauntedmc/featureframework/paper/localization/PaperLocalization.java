package nl.hauntedmc.featureframework.paper.localization;

import nl.hauntedmc.featureframework.localization.ComponentLocalization;
import nl.hauntedmc.featureframework.localization.LocalizationStore;
import nl.hauntedmc.featureframework.paper.integration.placeholder.PlaceholderAPIHook;
import nl.hauntedmc.featureframework.toolkit.io.config.ConfigService;
import nl.hauntedmc.featureframework.toolkit.io.localization.Language;
import nl.hauntedmc.featureframework.toolkit.log.FrameworkLogger;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Objects;
import java.util.function.Function;

/** Paper localization binding with caller-supplied player-language policy. */
public final class PaperLocalization extends ComponentLocalization {
    private final Plugin plugin;
    private final Function<Player, Language> playerLanguageResolver;

    public PaperLocalization(
            Plugin plugin,
            ConfigService configService,
            Function<Player, Language> playerLanguageResolver
    ) {
        this(
                Objects.requireNonNull(plugin, "plugin"),
                new LocalizationStore(
                        plugin.getClass().getClassLoader(),
                        configService,
                        FrameworkLogger.from(plugin.getLogger())
                ),
                playerLanguageResolver
        );
    }

    private PaperLocalization(
            Plugin plugin,
            LocalizationStore store,
            Function<Player, Language> playerLanguageResolver
    ) {
        super(
                store,
                FrameworkLogger.from(plugin.getLogger()),
                Player.class,
                audience -> playerLanguageResolver.apply((Player) audience),
                (message, audience) -> PlaceholderAPIHook.applyPlaceholders(message, (Player) audience),
                false
        );
        this.plugin = plugin;
        this.playerLanguageResolver = Objects.requireNonNull(playerLanguageResolver, "playerLanguageResolver");
    }

    public PaperLocalization openFeature(String featureName) {
        return new PaperLocalization(plugin, store().openFeature(featureName), playerLanguageResolver);
    }
}
