package nl.hauntedmc.featureframework.host;

import nl.hauntedmc.featureframework.feature.Feature;
import nl.hauntedmc.featureframework.lifecycle.FeatureLifecycleResources;
import nl.hauntedmc.featureframework.loader.ResolvedFeatureDefinition;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FeatureScopeFactoryTest {

    @Test
    void stableScopesAreReusedWhileLifecycleResourcesAreFresh() {
        Object config = new Object();
        Object localization = new Object();
        Object logger = new Object();
        AtomicInteger configCalls = new AtomicInteger();
        AtomicInteger resourceCalls = new AtomicInteger();
        java.util.List<TestResources> assembledResources = new java.util.ArrayList<>();
        FeatureScopeFactory<Feature, FeatureHostContext, Object, Object, Object, TestResources> factory =
                new FeatureScopeFactory<>(
                        ignored -> { configCalls.incrementAndGet(); return config; },
                        ignored -> localization,
                        ignored -> logger,
                        ignored -> new TestResources(resourceCalls.incrementAndGet()),
                        (descriptor, cfg, loc, log, resources) -> {
                            assertSame(config, cfg);
                            assertSame(localization, loc);
                            assertSame(logger, log);
                            assembledResources.add(resources);
                            return mock(FeatureHostContext.class);
                        }
                );

        factory.createContext(descriptor("Demo"));
        factory.createContext(descriptor("Demo"));

        assertEquals(1, configCalls.get());
        assertEquals(2, resourceCalls.get());
        assertNotSame(assembledResources.get(0), assembledResources.get(1));
        assertSame(config, factory.config(" Demo "));
    }

    @Test
    void clearForcesStableScopesToBeRecreated() {
        AtomicInteger configCalls = new AtomicInteger();
        FeatureScopeFactory<Feature, FeatureHostContext, Object, Object, Object, TestResources> factory =
                new FeatureScopeFactory<>(
                        ignored -> { configCalls.incrementAndGet(); return new Object(); },
                        ignored -> new Object(),
                        ignored -> new Object(),
                        ignored -> new TestResources(1),
                        (descriptor, config, localization, logger, resources) -> mock(FeatureHostContext.class)
                );

        Object before = factory.config("Demo");
        factory.clear();
        Object after = factory.config("Demo");

        assertNotSame(before, after);
        assertEquals(2, configCalls.get());
    }

    @SuppressWarnings("unchecked")
    private static ResolvedFeatureDefinition<Feature, FeatureHostContext> descriptor(String featureName) {
        ResolvedFeatureDefinition<Feature, FeatureHostContext> descriptor = mock(ResolvedFeatureDefinition.class);
        when(descriptor.featureName()).thenReturn(featureName);
        when(descriptor.registryName()).thenReturn(featureName.toLowerCase(java.util.Locale.ROOT));
        return descriptor;
    }

    private record TestResources(int generation) implements FeatureLifecycleResources {
        @Override public void quiesce() { }
        @Override public void cleanup() { }
    }
}
