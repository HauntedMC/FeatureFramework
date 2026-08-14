package nl.hauntedmc.featureframework.velocity.integration.dataprovider;

import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.plugin.PluginManager;
import nl.hauntedmc.dataprovider.api.DataProviderAPI;
import nl.hauntedmc.dataprovider.api.DataProviderApiSupplier;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VelocityDataProviderApiResolverTest {
    @Test
    void resolvesTheSupplierAndContainsLookupFailures() {
        PluginManager plugins = mock(PluginManager.class);
        PluginContainer container = mock(PluginContainer.class);
        DataProviderApiSupplier supplier = mock(DataProviderApiSupplier.class);
        DataProviderAPI api = mock(DataProviderAPI.class);
        when(plugins.getPlugin("dataprovider")).thenReturn(Optional.of(container));
        doReturn(Optional.of(supplier)).when(container).getInstance();
        when(supplier.dataProviderApi()).thenReturn(api);
        assertSame(api, VelocityDataProviderApiResolver.resolve(plugins, ignored -> { }));

        List<String> warnings = new ArrayList<>();
        when(supplier.dataProviderApi()).thenThrow(new IllegalStateException("not ready"));
        assertNull(VelocityDataProviderApiResolver.resolve(plugins, warnings::add));
        assertEquals(1, warnings.size());
    }
}
