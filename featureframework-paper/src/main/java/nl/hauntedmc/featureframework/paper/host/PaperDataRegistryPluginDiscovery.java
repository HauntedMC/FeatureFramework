package nl.hauntedmc.featureframework.paper.host;

import nl.hauntedmc.dataregistry.api.DataRegistryApiProvider;
import org.bukkit.plugin.Plugin;

import java.util.function.Supplier;

/** Lazily linked DataRegistry plugin discovery, isolated from DataRegistry-free Paper hosts. */
final class PaperDataRegistryPluginDiscovery {
    private PaperDataRegistryPluginDiscovery() {
    }

    static Supplier<?> supplier(Plugin host, String pluginName) {
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
