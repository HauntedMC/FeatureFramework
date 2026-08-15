package nl.hauntedmc.featureframework.host;

import nl.hauntedmc.featureframework.api.RuntimeState;
import nl.hauntedmc.featureframework.api.feature.FeatureId;
import nl.hauntedmc.featureframework.api.feature.FeatureRole;
import nl.hauntedmc.featureframework.api.feature.FeatureState;
import nl.hauntedmc.featureframework.config.DefaultFeatureConfiguration;
import nl.hauntedmc.featureframework.config.FeatureConfigHandler;
import nl.hauntedmc.featureframework.lifecycle.FeatureLifecycleResources;
import nl.hauntedmc.featureframework.loader.ResolvedFeatureDefinition;
import nl.hauntedmc.featureframework.localization.LocalizationStore;
import nl.hauntedmc.featureframework.operation.reset.FeatureFileResetRequest;
import nl.hauntedmc.featureframework.operation.reset.FeatureResetRuntimeOutcome;
import nl.hauntedmc.featureframework.operation.reset.MessageResetScope;
import nl.hauntedmc.featureframework.runtime.FeatureRuntime;
import nl.hauntedmc.featureframework.service.DefaultCapabilityRegistry;
import nl.hauntedmc.featureframework.service.FeatureServiceManager;
import nl.hauntedmc.featureframework.toolkit.io.config.ConfigService;
import nl.hauntedmc.featureframework.toolkit.log.FrameworkLogger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeatureHostTest {
    @TempDir Path temporaryDirectory;

    @Test
    void hostsCollectionAndReloadsProviderWithDependent() {
        ProviderFeature.starts.set(0);
        ConsumerFeature.starts.set(0);
        List<String> startupSequence = new ArrayList<>();
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
                        context -> new ProviderFeature(context, startupSequence))
                .providesCapabilities(GreetingApi.class)
                .enabledByDefault()
                .build();
        FeatureDefinition<TestFeature, TestContext> consumer = FeatureDefinition.<TestFeature, TestContext>builder(
                        "Consumer", "1.0.0", ConsumerFeature.class,
                        context -> new ConsumerFeature(context, startupSequence))
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
        assertEquals(List.of("provider", "consumer"), startupSequence);
        assertEquals(2, host.features().snapshot().size());
        assertEquals(Set.of(FeatureRole.CAPABILITY_PROVIDER),
                host.features().find(FeatureId.of("Provider")).orElseThrow().metadata().roles());
        assertEquals(Set.of(FeatureRole.CAPABILITY_CONSUMER),
                host.features().find(FeatureId.of("Consumer")).orElseThrow().metadata().roles());
        assertTrue(host.features().snapshot().stream()
                .allMatch(snapshot -> snapshot.state() == FeatureState.ACTIVE));
        var missingPreview = host.previewFileReset(FeatureId.of("Missing"), FeatureFileResetRequest.config());
        assertFalse(missingPreview.valid());
        assertTrue(missingPreview.feature().isBlank());
        assertEquals("hello-1", host.capabilities().reference(GreetingApi.class).require().greeting());
        long firstGeneration = host.capabilities().reference(GreetingApi.class).generation().orElseThrow();

        assertTrue(host.recreate(FeatureId.of("Provider")).success());

        assertEquals(2, ProviderFeature.starts.get());
        assertEquals(2, ConsumerFeature.starts.get());
        assertEquals("hello-2", host.capabilities().reference(GreetingApi.class).require().greeting());
        assertTrue(host.capabilities().reference(GreetingApi.class).generation().orElseThrow() > firstGeneration);
        assertTrue(transitions.contains(FeatureState.STOPPING));

        assertTrue(host.reloadGraph().success());
        assertEquals(RuntimeState.READY, host.state());
        assertEquals(3, ProviderFeature.starts.get());
        assertEquals(3, ConsumerFeature.starts.get());
        assertEquals("hello-3", host.capabilities().reference(GreetingApi.class).require().greeting());

        configuration.openFeatureConfig("Provider").put("obsolete", "remove-me");
        var configReset = host.resetFiles(FeatureId.of("Provider"), FeatureFileResetRequest.config());
        assertTrue(configReset.success());
        assertEquals(FeatureResetRuntimeOutcome.ACTIVE, configReset.runtimeOutcome());
        assertTrue(configReset.affectedDependents().contains("Consumer"));
        assertNull(configuration.openFeatureConfig("Provider").get("obsolete"));
        assertTrue(configuration.isFeatureEnabled("Provider"));
        assertEquals(4, ProviderFeature.starts.get());
        assertEquals(4, ConsumerFeature.starts.get());

        Path providerDirectory = temporaryDirectory.resolve("features/Provider");
        try {
            Files.writeString(providerDirectory.resolve("messages_EN.yml"), "custom: english\n");
            Files.writeString(providerDirectory.resolve("messages_old-LANG.yml"), "custom: stale\n");
        } catch (java.io.IOException failure) {
            throw new AssertionError(failure);
        }
        var messagesReset = host.resetFiles(FeatureId.of("Provider"), FeatureFileResetRequest.messages(
                MessageResetScope.MAIN_AND_OVERRIDES));
        assertTrue(messagesReset.success());
        assertEquals(2, messagesReset.deletedOverrides().size());
        assertTrue(Files.notExists(providerDirectory.resolve("messages_EN.yml")));
        assertTrue(Files.notExists(providerDirectory.resolve("messages_old-LANG.yml")));

        host.stop();

        assertEquals(RuntimeState.STOPPED, host.state());
        assertTrue(host.features().snapshot().stream()
                .allMatch(snapshot -> snapshot.state() == FeatureState.DISABLED));
    }

    private static TestContext context(
            ResolvedFeatureDefinition<TestFeature, TestContext> descriptor,
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
        private final List<String> startupSequence;

        TestFeature(TestContext context, List<String> startupSequence) {
            super(context);
            this.startupSequence = startupSequence;
        }

        void started(String feature) {
            startupSequence.add(feature);
        }

        @Override public void disable() { }
    }

    static final class ProviderFeature extends TestFeature {
        private static final AtomicInteger starts = new AtomicInteger();

        ProviderFeature(TestContext context, List<String> startupSequence) {
            super(context, startupSequence);
        }

        @Override
        public void initialize() {
            int generation = starts.incrementAndGet();
            context().services().registerService(GreetingApi.class, () -> "hello-" + generation);
            started("provider");
        }
    }

    static final class ConsumerFeature extends TestFeature {
        private static final AtomicInteger starts = new AtomicInteger();

        ConsumerFeature(TestContext context, List<String> startupSequence) {
            super(context, startupSequence);
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
                ResolvedFeatureDefinition<?, ?> descriptor,
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
