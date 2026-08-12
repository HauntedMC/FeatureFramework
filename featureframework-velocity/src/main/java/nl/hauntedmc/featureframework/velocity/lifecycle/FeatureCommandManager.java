package nl.hauntedmc.featureframework.velocity.lifecycle;

import com.velocitypowered.api.command.CommandManager;
import nl.hauntedmc.featureframework.velocity.command.CommandOwnershipRegistry;
import org.slf4j.Logger;

/**
 * Compatibility facade preserving the original public package while command ownership implementation
 * lives with the Velocity command adapter.
 *
 * <p>New framework internals should prefer
 * {@link nl.hauntedmc.featureframework.velocity.command.FeatureCommandManager}; this facade remains a
 * supported compatibility type for existing consumers.</p>
 */
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
