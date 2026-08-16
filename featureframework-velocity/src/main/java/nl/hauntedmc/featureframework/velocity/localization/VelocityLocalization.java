package nl.hauntedmc.featureframework.velocity.localization;

import com.velocitypowered.api.proxy.Player;
import nl.hauntedmc.featureframework.localization.ComponentLocalization;
import nl.hauntedmc.featureframework.localization.LocalizationStore;
import nl.hauntedmc.featureframework.toolkit.io.config.ConfigService;
import nl.hauntedmc.featureframework.toolkit.io.localization.Language;
import nl.hauntedmc.featureframework.toolkit.log.FrameworkLogger;
import nl.hauntedmc.featureframework.theme.ThemeRegistry;
import org.slf4j.Logger;

import java.util.Objects;
import java.util.function.Function;

/** Velocity localization binding with caller-supplied player-language policy. */
public final class VelocityLocalization extends ComponentLocalization {
    private final Logger platformLogger;
    private final Function<Player, Language> playerLanguageResolver;

    public VelocityLocalization(
            Logger logger,
            ClassLoader resources,
            ConfigService configService,
            Function<Player, Language> playerLanguageResolver
    ) {
        this(logger, resources, configService, playerLanguageResolver, ThemeRegistry.empty());
    }

    public VelocityLocalization(
            Logger logger,
            ClassLoader resources,
            ConfigService configService,
            Function<Player, Language> playerLanguageResolver,
            ThemeRegistry themes
    ) {
        this(
                logger,
                new LocalizationStore(resources, configService, FrameworkLogger.from(logger)),
                playerLanguageResolver,
                themes
        );
    }

    private VelocityLocalization(
            Logger logger,
            LocalizationStore store,
            Function<Player, Language> playerLanguageResolver,
            ThemeRegistry themes
    ) {
        super(
                store,
                FrameworkLogger.from(logger),
                Player.class,
                audience -> playerLanguageResolver.apply((Player) audience),
                (message, player) -> message,
                true,
                themes
        );
        platformLogger = Objects.requireNonNull(logger, "logger");
        this.playerLanguageResolver = Objects.requireNonNull(playerLanguageResolver, "playerLanguageResolver");
    }

    public VelocityLocalization openFeature(String featureName) {
        return new VelocityLocalization(
                platformLogger, store().openFeature(featureName), playerLanguageResolver, themes());
    }
}
