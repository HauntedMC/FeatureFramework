package nl.hauntedmc.featureframework.paper.integration.dataregistry;

import nl.hauntedmc.dataregistry.api.DataRegistryApi;
import nl.hauntedmc.featureframework.integration.dataregistry.DataRegistryResources;
import nl.hauntedmc.featureframework.integration.dataregistry.PlayerReferenceResolver;
import nl.hauntedmc.featureframework.paper.lifecycle.PaperFeatureResources;
import nl.hauntedmc.featureframework.paper.log.FeatureLogger;
import org.bukkit.plugin.Plugin;

/** Reusable DataRegistry access contract for Paper features without imposing a specialized base class. */
public interface PaperDataRegistryAccess extends PaperDataRegistryIdentityGate.Context {
    PaperFeatureResources resources();
    FeatureLogger logger();
    Plugin plugin();

    @Override default DataRegistryApi dataRegistry() {
        return resources().extensions().require(DataRegistryResources.KEY).registry();
    }

    default PlayerReferenceResolver playerReferences() {
        return resources().extensions().require(DataRegistryResources.KEY).players();
    }

    @Override default void scheduleContinuation(Runnable continuation) {
        resources().tasks().scheduleOneTimeTask(continuation);
    }

    @Override default boolean hostAvailable() {
        return plugin().isEnabled();
    }

    @Override default void warn(String message) {
        logger().warning(message);
    }
}
