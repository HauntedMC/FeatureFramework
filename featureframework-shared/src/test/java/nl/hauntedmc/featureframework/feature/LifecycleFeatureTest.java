package nl.hauntedmc.featureframework.feature;

import nl.hauntedmc.featureframework.config.FeatureConfigHandler;
import nl.hauntedmc.featureframework.lifecycle.FeatureLifecycleResources;
import nl.hauntedmc.featureframework.toolkit.io.config.ConfigMap;
import nl.hauntedmc.featureframework.toolkit.io.localization.MessageMap;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LifecycleFeatureTest {

    @Test
    void cleanupUsesTheSameSafeOrderForEveryPlatformBinding() {
        List<String> events = new ArrayList<>();
        FeatureRuntimeContext context = context(events);
        TestFeature feature = new TestFeature(context, events, false);

        feature.cleanup();

        assertEquals(List.of("started", "config", "before", "quiesce", "services", "disable", "cleanup"), events);
    }

    @Test
    void cleanupStillReleasesResourcesWhenDisableFails() {
        List<String> events = new ArrayList<>();
        FeatureRuntimeContext context = context(events);
        TestFeature feature = new TestFeature(context, events, true);

        assertThrows(IllegalStateException.class, feature::cleanup);

        assertEquals(List.of("started", "config", "before", "quiesce", "services", "disable", "cleanup"), events);
    }

    private static FeatureRuntimeContext context(List<String> events) {
        FeatureRuntimeContext context = mock(FeatureRuntimeContext.class);
        FeatureConfigHandler config = mock(FeatureConfigHandler.class);
        FeatureLifecycleResources lifecycle = mock(FeatureLifecycleResources.class);
        when(context.configHandler()).thenReturn(config);
        when(context.lifecycle()).thenReturn(lifecycle);
        doAnswer(ignored -> {
            events.add("config");
            return null;
        }).when(config).clearReloadListeners();
        doAnswer(ignored -> {
            events.add("quiesce");
            return null;
        }).when(lifecycle).quiesce();
        doAnswer(ignored -> {
            events.add("services");
            return null;
        }).when(context).deactivateServices();
        doAnswer(ignored -> {
            events.add("cleanup");
            return null;
        }).when(lifecycle).cleanup();
        return context;
    }

    private static final class TestFeature extends LifecycleFeature<FeatureRuntimeContext> {
        private final List<String> events;
        private final boolean failDisable;

        private TestFeature(FeatureRuntimeContext context, List<String> events, boolean failDisable) {
            super(context);
            this.events = events;
            this.failDisable = failDisable;
        }

        @Override protected void onCleanupStarted() {
            events.add("started");
        }

        @Override protected void beforeLifecycleQuiesce() {
            events.add("before");
        }

        @Override public ConfigMap getDefaultConfig() {
            return new ConfigMap();
        }

        @Override public MessageMap getDefaultMessages() {
            return new MessageMap();
        }

        @Override public void initialize() {
        }

        @Override public void disable() {
            events.add("disable");
            if (failDisable) {
                throw new IllegalStateException("disable failed");
            }
        }
    }
}
