package nl.hauntedmc.featureframework.velocity.integration.dataregistry;

import com.velocitypowered.api.proxy.ProxyServer;
import nl.hauntedmc.dataregistry.api.DataRegistryApi;
import nl.hauntedmc.dataregistry.api.DataRegistryApiProvider;

import java.util.function.Supplier;

/** Lazily discovers DataRegistry without coupling DataRegistry-free Velocity hosts to its API. */
public final class VelocityDataRegistryPluginDiscovery {
    private VelocityDataRegistryPluginDiscovery() {
    }

    public static Supplier<DataRegistryApi> supplier(ProxyServer proxy, String pluginId) {
        return () -> proxy.getPluginManager().getPlugin(pluginId)
                .flatMap(container -> container.getInstance())
                .filter(DataRegistryApiProvider.class::isInstance)
                .map(DataRegistryApiProvider.class::cast)
                .map(DataRegistryApiProvider::getDataRegistry)
                .orElseThrow(() -> new IllegalStateException(
                        "Plugin '" + pluginId + "' does not provide DataRegistryApi"));
    }
}
