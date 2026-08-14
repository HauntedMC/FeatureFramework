package nl.hauntedmc.featureframework.paper.integration.dataregistry;

import nl.hauntedmc.dataregistry.api.DataRegistryApi;
import nl.hauntedmc.dataregistry.api.DataRegistryApiProvider;
import org.bukkit.plugin.Plugin;

import java.util.function.Supplier;

/** Lazily discovers DataRegistry without coupling DataRegistry-free Paper hosts to its API. */
public final class PaperDataRegistryPluginDiscovery {
    private PaperDataRegistryPluginDiscovery() {
    }

    public static Supplier<DataRegistryApi> supplier(Plugin host, String pluginName) {
        return () -> {
            Plugin candidate = host.getServer().getPluginManager().getPlugin(pluginName);
            if (!(candidate instanceof DataRegistryApiProvider provider)) {
                throw new IllegalStateException(
                        "Plugin '" + pluginName + "' does not provide DataRegistryApi");
            }
            return provider.getDataRegistry();
        };
    }
}
