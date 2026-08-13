package nl.hauntedmc.featureframework.velocity.host;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import nl.hauntedmc.dataregistry.api.DataRegistryApi;
import nl.hauntedmc.dataregistry.api.player.PlayerData;
import nl.hauntedmc.dataregistry.api.player.PlayerDirectory;
import nl.hauntedmc.featureframework.api.feature.FeatureId;
import nl.hauntedmc.featureframework.api.service.CapabilityRegistry;
import nl.hauntedmc.featureframework.config.FeatureConfigHandler;
import nl.hauntedmc.featureframework.loader.FeatureDescriptor;
import nl.hauntedmc.featureframework.service.FeatureServiceManager;
import nl.hauntedmc.featureframework.service.InternalServiceRegistry;
import nl.hauntedmc.featureframework.toolkit.io.config.ConfigMap;
import nl.hauntedmc.featureframework.toolkit.io.localization.MessageMap;
import nl.hauntedmc.featureframework.velocity.lifecycle.FeatureTaskManager;
import nl.hauntedmc.featureframework.velocity.lifecycle.VelocityFeatureResources;
import nl.hauntedmc.featureframework.velocity.localization.VelocityLocalization;
import nl.hauntedmc.featureframework.velocity.log.FeatureLogger;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VelocityFeatureContextTest {

    @Test
    void exposesTypedPlatformResourcesAndTheirServiceManager() {
        Object plugin = new Object();
        FeatureDescriptor<?, ?> descriptor = mock();
        FeatureConfigHandler config = mock();
        VelocityLocalization localization = mock();
        VelocityFeatureResources<String> resources = mock();
        FeatureLogger logger = mock();
        CapabilityRegistry capabilities = mock();
        InternalServiceRegistry<FeatureId> internalServices = mock();
        FeatureServiceManager<FeatureId> services = mock();
        when(resources.getApiManager()).thenReturn(services);

        VelocityFeatureContext<Object, String> context = new VelocityFeatureContext<>(
                plugin, descriptor, config, localization, resources, logger, capabilities, internalServices);

        assertSame(plugin, context.plugin());
        assertSame(resources, context.resources());
        assertSame(localization, context.localization());
        assertSame(services, context.services());
    }

    @Test
    void dataRegistryFeatureOwnsPlatformGateAndCanonicalAccessors() {
        Object plugin = new Object();
        DataRegistryApi dataRegistry = mock();
        PlayerData playerData = mock();
        PlayerDirectory playerDirectory = mock();
        ProxyServer proxy = mock();
        Player player = mock();
        UUID playerId = UUID.randomUUID();
        FeatureDescriptor<?, ?> descriptor = mock();
        FeatureConfigHandler config = mock();
        VelocityLocalization localization = mock();
        VelocityFeatureResources<String> resources = mock();
        FeatureTaskManager tasks = mock();
        FeatureLogger logger = mock();
        CapabilityRegistry capabilities = mock();
        InternalServiceRegistry<FeatureId> internalServices = mock();
        FeatureServiceManager<FeatureId> services = mock();
        when(resources.getApiManager()).thenReturn(services);
        when(resources.getTaskManager()).thenReturn(tasks);
        when(resources.getDataManager()).thenReturn("data");
        when(dataRegistry.players()).thenReturn(playerData);
        when(playerData.identities()).thenReturn(playerDirectory);
        when(proxy.getPlayer(playerId)).thenReturn(Optional.of(player));
        VelocityFeatureContext<Object, String> context = new VelocityFeatureContext<>(
                plugin, descriptor, config, localization, resources, logger, capabilities,
                internalServices, proxy, () -> dataRegistry);
        TestDataRegistryFeature feature = new TestDataRegistryFeature(context);
        Runnable continuation = () -> { };

        assertSame(plugin, feature.plugin());
        assertSame(logger, feature.logger());
        assertSame(resources, feature.resources());
        assertSame(localization, feature.localization());
        assertSame(dataRegistry, feature.dataRegistry());
        assertSame(feature.playerReferences(), feature.playerReferences());
        assertSame(player, feature.connectedPlayer(playerId).orElseThrow());
        assertSame("data", feature.exposedDataManager());
        feature.scheduleContinuation(continuation);
        feature.warn("warning");

        verify(tasks).scheduleTask(continuation);
        verify(logger).warn("warning");
    }

    private static final class TestDataRegistryFeature
            extends VelocityDataRegistryFeature<Object, String> {
        private TestDataRegistryFeature(
                VelocityFeatureContext<Object, String> context
        ) {
            super(context);
        }

        private String exposedDataManager() { return dataManager(); }
        @Override public ConfigMap getDefaultConfig() { return new ConfigMap(); }
        @Override public MessageMap getDefaultMessages() { return new MessageMap(); }
        @Override public void initialize() { }
        @Override public void disable() { }
    }
}
