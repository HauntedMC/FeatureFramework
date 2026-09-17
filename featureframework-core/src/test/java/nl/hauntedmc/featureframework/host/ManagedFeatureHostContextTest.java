package nl.hauntedmc.featureframework.host;

import nl.hauntedmc.featureframework.config.FeatureConfigHandler;
import nl.hauntedmc.featureframework.feature.Feature;
import nl.hauntedmc.featureframework.localization.FeatureLocalization;
import nl.hauntedmc.featureframework.toolkit.io.config.ConfigMap;
import nl.hauntedmc.featureframework.toolkit.io.localization.MessageMap;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ManagedFeatureHostContextTest {

    @Test
    void storagePreflightMaterializesDefaultsWithoutReloadingRuntimeState() {
        ManagedFeatureHostContext context = mock(ManagedFeatureHostContext.class, CALLS_REAL_METHODS);
        FeatureConfigHandler config = mock(FeatureConfigHandler.class);
        FeatureLocalization localization = mock(FeatureLocalization.class);
        Feature feature = mock(Feature.class);
        ConfigMap defaults = new ConfigMap();
        MessageMap messages = new MessageMap();

        when(context.configHandler()).thenReturn(config);
        when(context.localization()).thenReturn(localization);
        when(feature.defaultConfig()).thenReturn(defaults);
        when(feature.defaultMessages()).thenReturn(messages);

        context.prepareStorage(feature);

        verify(config).injectDefaults(defaults);
        verify(localization).registerDefaultMessages(messages);
        verify(config, never()).reloadConfig();
        verify(localization, never()).reloadLocalization();
        verify(localization, never()).reloadLocalizationQuietly();
    }

    @Test
    void runtimePreparationReloadsConfigAndLocalizationExactlyOnce() {
        ManagedFeatureHostContext context = mock(ManagedFeatureHostContext.class, CALLS_REAL_METHODS);
        FeatureConfigHandler config = mock(FeatureConfigHandler.class);
        FeatureLocalization localization = mock(FeatureLocalization.class);
        Feature feature = mock(Feature.class);
        ConfigMap defaults = new ConfigMap();
        MessageMap messages = new MessageMap();

        when(context.configHandler()).thenReturn(config);
        when(context.localization()).thenReturn(localization);
        when(feature.defaultConfig()).thenReturn(defaults);
        when(feature.defaultMessages()).thenReturn(messages);

        context.prepare(feature);

        verify(config).injectDefaults(defaults);
        verify(localization).registerDefaultMessages(messages);
        verify(config).reloadConfig();
        verify(localization).reloadLocalizationQuietly();
        verify(localization, never()).reloadLocalization();
    }
}
