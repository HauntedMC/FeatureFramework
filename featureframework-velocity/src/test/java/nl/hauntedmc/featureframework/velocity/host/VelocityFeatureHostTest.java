package nl.hauntedmc.featureframework.velocity.host;

import com.velocitypowered.api.plugin.PluginManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VelocityFeatureHostTest {

    @Test
    void resolvesDeclaredPluginNamesAsVelocityPluginIds() {
        PluginManager plugins = mock();
        when(plugins.isLoaded("dataprovider")).thenReturn(true);

        assertTrue(VelocityFeatureHost.platformPluginAvailable(plugins, "DataProvider"));
        assertFalse(VelocityFeatureHost.platformPluginAvailable(plugins, "MissingPlugin"));
        verify(plugins).isLoaded("dataprovider");
        verify(plugins).isLoaded("missingplugin");
    }
}
