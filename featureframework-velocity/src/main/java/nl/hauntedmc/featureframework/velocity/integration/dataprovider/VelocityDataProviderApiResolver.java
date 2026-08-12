package nl.hauntedmc.featureframework.velocity.integration.dataprovider;

import com.velocitypowered.api.plugin.PluginManager;
import nl.hauntedmc.dataprovider.api.DataProviderAPI;
import nl.hauntedmc.dataprovider.api.DataProviderApiSupplier;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Discovers DataProvider through Velocity's plugin registry. */
public final class VelocityDataProviderApiResolver {
    private static final String DATA_PROVIDER_PLUGIN_ID = "dataprovider";

    private VelocityDataProviderApiResolver() {
    }

    public static Supplier<DataProviderAPI> supplier(
            Supplier<PluginManager> pluginManager,
            Consumer<String> warningSink
    ) {
        Objects.requireNonNull(pluginManager, "pluginManager");
        Objects.requireNonNull(warningSink, "warningSink");
        return () -> resolve(pluginManager.get(), warningSink);
    }

    public static DataProviderAPI resolve(PluginManager pluginManager, Consumer<String> warningSink) {
        Objects.requireNonNull(pluginManager, "pluginManager");
        Objects.requireNonNull(warningSink, "warningSink");
        try {
            return pluginManager.getPlugin(DATA_PROVIDER_PLUGIN_ID)
                    .flatMap(container -> container.getInstance())
                    .filter(DataProviderApiSupplier.class::isInstance)
                    .map(DataProviderApiSupplier.class::cast)
                    .map(DataProviderApiSupplier::dataProviderApi)
                    .orElse(null);
        } catch (RuntimeException failure) {
            warningSink.accept("DataProviderAPI unavailable: " + failure.getMessage());
            return null;
        }
    }
}
