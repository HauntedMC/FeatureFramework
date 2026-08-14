package nl.hauntedmc.featureframework.paper.integration.dataprovider;

import nl.hauntedmc.dataprovider.api.DataProviderAPI;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicesManager;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Discovers DataProvider through Paper's service registry without coupling feature plugins to discovery details. */
public final class PaperDataProviderApiResolver {
    private PaperDataProviderApiResolver() {
    }

    public static Supplier<DataProviderAPI> supplier(
            Supplier<ServicesManager> servicesManager,
            Consumer<String> warningSink
    ) {
        Objects.requireNonNull(servicesManager, "servicesManager");
        Objects.requireNonNull(warningSink, "warningSink");
        return () -> resolve(servicesManager.get(), warningSink);
    }

    public static DataProviderAPI resolve(ServicesManager servicesManager, Consumer<String> warningSink) {
        Objects.requireNonNull(warningSink, "warningSink");
        try {
            RegisteredServiceProvider<DataProviderAPI> registration = Objects.requireNonNull(
                    servicesManager, "servicesManager").getRegistration(DataProviderAPI.class);
            return registration == null ? null : registration.getProvider();
        } catch (RuntimeException failure) {
            warningSink.accept("DataProviderAPI unavailable: " + failure.getMessage());
            return null;
        }
    }
}
