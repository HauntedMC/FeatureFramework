package nl.hauntedmc.featureframework.host;

import nl.hauntedmc.featureframework.api.RuntimeState;
import nl.hauntedmc.featureframework.api.feature.FeatureId;
import nl.hauntedmc.featureframework.api.feature.FeatureClassification;
import nl.hauntedmc.featureframework.api.feature.FeatureState;
import nl.hauntedmc.featureframework.config.DefaultFeatureConfiguration;
import nl.hauntedmc.featureframework.config.FeatureConfigHandler;
import nl.hauntedmc.featureframework.lifecycle.FeatureLifecycleResources;
import nl.hauntedmc.featureframework.loader.FeatureDescriptor;
import nl.hauntedmc.featureframework.localization.LocalizationStore;
import nl.hauntedmc.featureframework.runtime.FeatureRuntime;
import nl.hauntedmc.featureframework.service.DefaultCapabilityRegistry;
import nl.hauntedmc.featureframework.service.FeatureServiceManager;
import nl.hauntedmc.featureframework.toolkit.io.config.ConfigService;
import nl.hauntedmc.featureframework.toolkit.log.FrameworkLogger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeatureHostTest {
    @TempDir Path temporaryDirectory;

    @Test
    void hostsCollectionAndReloadsProviderWithDependent() {
        ProviderFeature.starts.set(0);
        ConsumerFeature.starts.set(0);
        List<String> startupOrder = new ArrayList<>();
        List<FeatureState> transitions = new ArrayList<>();
        FrameworkLogger logger = FrameworkLogger.noop();
        ConfigService configService = new ConfigService(
                temporaryDirectory, logger, getClass().getClassLoader());
        DefaultFeatureConfiguration configuration = new DefaultFeatureConfiguration(configService, logger);
        LocalizationStore localization = new LocalizationStore(
                getClass().getClassLoader(), configService, logger);
        DefaultCapabilityRegistry capabilities = new DefaultCapabilityRegistry(
                getClass().getPackageName(), getClass().getClassLoader());
        FeatureRuntime<FeatureId, DefaultCapabilityRegistry> runtime =
                new FeatureRuntime<>("test-host", capabilities);

        FeatureDefinition<TestFeature, TestContext> provider = FeatureDefinition.<TestFeature, TestContext>builder(
                        "Provider", "1.0.0", ProviderFeature.class,
                        context -> new ProviderFeature(context, startupOrder))
                .providesCapabilities(GreetingApi.class)
                .enabledByDefault()
                .build();
        FeatureDefinition<TestFeature, TestContext> consumer = FeatureDefinition.<TestFeature, TestContext>builder(
                        "Consumer", "1.0.0", ConsumerFeature.class,
                        context -> new ConsumerFeature(context, startupOrder))
                .requiresCapabilities(GreetingApi.class)
                .enabledByDefault()
                .build();
        FeatureCollection<TestFeature, TestContext> features = FeatureCollection.of(consumer, provider);

        FeatureHost<String, TestFeature, TestContext> host = FeatureHost.builder(
                        "test-host", "1.0.0", "test", runtime, configuration, features)
                .contextFactory(descriptor -> context(
                        descriptor, configuration, localization, runtime, logger))
                .logger(logger)
                .build();
        host.features().subscribe(snapshot -> transitions.add(snapshot.state()));

        host.start();

        assertEquals(RuntimeState.READY, host.state());
        assertEquals(List.of("provider", "consumer"), startupOrder);
        assertEquals(2, host.features().snapshot().size());
        assertEquals(FeatureClassification.CAPABILITY_PROVIDER,
                host.features().find(FeatureId.of("Provider")).orElseThrow().descriptor().classification());
        assertEquals(FeatureClassification.CAPABILITY_CONSUMER,
                host.features().find(FeatureId.of("Consumer")).orElseThrow().descriptor().classification());
        assertTrue(host.features().snapshot().stream()
                .allMatch(snapshot -> snapshot.state() == FeatureState.ACTIVE));
        assertEquals("hello-1", host.capabilities().reference(GreetingApi.class).require().greeting());
        long firstGeneration = host.capabilities().reference(GreetingApi.class).generation().orElseThrow();

        assertTrue(host.reloadFeature("Provider").success());

        assertEquals(2, ProviderFeature.starts.get());
        assertEquals(2, ConsumerFeature.starts.get());
        assertEquals("hello-2", host.capabilities().reference(GreetingApi.class).require().greeting());
        assertTrue(host.capabilities().reference(GreetingApi.class).generation().orElseThrow() > firstGeneration);
        assertTrue(transitions.contains(FeatureState.STOPPING));

        assertTrue(host.reload().success());
        assertEquals(RuntimeState.READY, host.state());
        assertEquals(3, ProviderFeature.starts.get());
        assertEquals(3, ConsumerFeature.starts.get());
        assertEquals("hello-3", host.capabilities().reference(GreetingApi.class).require().greeting());

        host.stop();

        assertEquals(RuntimeState.STOPPED, host.state());
        assertTrue(host.features().snapshot().stream()
                .allMatch(snapshot -> snapshot.state() == FeatureState.DISABLED));
    }

    private static TestContext context(
            FeatureDescriptor<TestFeature, TestContext> descriptor,
            DefaultFeatureConfiguration configuration,
            LocalizationStore localization,
            FeatureRuntime<FeatureId, DefaultCapabilityRegistry> runtime,
            FrameworkLogger logger
    ) {
        FeatureServiceManager<FeatureId> services = new FeatureServiceManager<>();
        services.bindRegistries(runtime.capabilities(), runtime.internalServices(),
                FeatureId.of(descriptor.registryName()));
        return new TestContext(
                descriptor,
                configuration.openFeatureConfig(descriptor.featureName()),
                localization.openFeature(descriptor.featureName()),
                new TestResources(),
                logger,
                runtime,
                services
        );
    }

    public interface GreetingApi {
        String greeting();
    }

    abstract static class TestFeature extends ManagedFeature<TestContext> {
        private final List<String> startupOrder;

        TestFeature(TestContext context, List<String> startupOrder) {
            super(context);
            this.startupOrder = startupOrder;
        }

        void started(String feature) {
            startupOrder.add(feature);
        }

        @Override public void disable() { }
    }

    static final class ProviderFeature extends TestFeature {
        private static final AtomicInteger starts = new AtomicInteger();

        ProviderFeature(TestContext context, List<String> startupOrder) {
            super(context, startupOrder);
        }

        @Override
        public void initialize() {
            int generation = starts.incrementAndGet();
            getContext().services().registerService(GreetingApi.class, () -> "hello-" + generation);
            started("provider");
        }
    }

    static final class ConsumerFeature extends TestFeature {
        private static final AtomicInteger starts = new AtomicInteger();

        ConsumerFeature(TestContext context, List<String> startupOrder) {
            super(context, startupOrder);
        }

        @Override
        public void initialize() {
            requireCapability(GreetingApi.class);
            starts.incrementAndGet();
            started("consumer");
        }
    }

    static final class TestContext
            extends ManagedFeatureContext<Object, TestResources, FrameworkLogger, LocalizationStore> {
        TestContext(
                FeatureDescriptor<?, ?> descriptor,
                FeatureConfigHandler config,
                LocalizationStore localization,
                TestResources resources,
                FrameworkLogger logger,
                FeatureRuntime<FeatureId, DefaultCapabilityRegistry> runtime,
                FeatureServiceManager<FeatureId> services
        ) {
            super(new Object(), descriptor, config, localization, resources, logger,
                    runtime.capabilities(), runtime.internalServices(), services);
        }
    }

    static final class TestResources implements FeatureLifecycleResources {
        @Override public void quiesce() { }
        @Override public void cleanup() { }
    }
}
