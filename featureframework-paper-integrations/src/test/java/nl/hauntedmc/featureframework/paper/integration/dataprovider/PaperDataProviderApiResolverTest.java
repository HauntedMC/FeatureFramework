package nl.hauntedmc.featureframework.paper.integration.dataprovider;

import nl.hauntedmc.dataprovider.api.DataProviderAPI;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicesManager;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PaperDataProviderApiResolverTest {
    @Test
    void resolvesTheRegisteredProviderAndContainsLookupFailures() {
        ServicesManager services = mock(ServicesManager.class);
        @SuppressWarnings("unchecked")
        RegisteredServiceProvider<DataProviderAPI> registration = mock(RegisteredServiceProvider.class);
        DataProviderAPI api = mock(DataProviderAPI.class);
        when(services.getRegistration(DataProviderAPI.class)).thenReturn(registration);
        when(registration.getProvider()).thenReturn(api);
        assertSame(api, PaperDataProviderApiResolver.resolve(services, ignored -> { }));

        List<String> warnings = new ArrayList<>();
        when(services.getRegistration(DataProviderAPI.class)).thenThrow(new IllegalStateException("not ready"));
        assertNull(PaperDataProviderApiResolver.resolve(services, warnings::add));
        assertEquals(1, warnings.size());
    }
}
