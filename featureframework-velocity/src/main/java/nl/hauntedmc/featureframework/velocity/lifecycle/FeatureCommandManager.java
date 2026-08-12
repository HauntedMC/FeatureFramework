package nl.hauntedmc.featureframework.velocity.lifecycle;

import com.velocitypowered.api.command.CommandManager;
import nl.hauntedmc.featureframework.velocity.command.CommandOwnershipRegistry;
import org.slf4j.Logger;

/**
 * Compatibility facade for the pre-1.2 command-manager package.
 *
 * @deprecated Command ownership belongs to the Velocity command adapter. Use
 * {@link nl.hauntedmc.featureframework.velocity.command.FeatureCommandManager} for new code.
 */
@Deprecated(forRemoval = false, since = "1.2")
public class FeatureCommandManager
        extends nl.hauntedmc.featureframework.velocity.command.FeatureCommandManager {

    public FeatureCommandManager(
            Object plugin,
            CommandManager commandManager,
            CommandOwnershipRegistry ownershipRegistry,
            Logger logger,
            String featureName
    ) {
        super(plugin, commandManager, ownershipRegistry, logger, featureName);
    }
}
