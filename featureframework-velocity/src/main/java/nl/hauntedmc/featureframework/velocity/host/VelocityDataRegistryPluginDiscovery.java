package nl.hauntedmc.featureframework.velocity.host;

import com.velocitypowered.api.proxy.ProxyServer;
import nl.hauntedmc.dataregistry.api.DataRegistryApiProvider;

import java.util.function.Supplier;

/** Lazily linked DataRegistry plugin discovery, isolated from DataRegistry-free Velocity hosts. */
final class VelocityDataRegistryPluginDiscovery {
    private VelocityDataRegistryPluginDiscovery() {
    }

    static Supplier<?> supplier(ProxyServer proxy, String pluginId) {
        return () -> proxy.getPluginManager().getPlugin(pluginId)
                .flatMap(container -> container.getInstance())
                .filter(DataRegistryApiProvider.class::isInstance)
                .map(DataRegistryApiProvider.class::cast)
                .map(DataRegistryApiProvider::getDataRegistry)
                .orElseThrow(() -> new IllegalStateException(
                        "Plugin '" + pluginId + "' does not provide DataRegistryApi"));
    }
}
