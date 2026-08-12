package nl.hauntedmc.featureframework.acceptance.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import nl.hauntedmc.featureframework.api.RuntimeState;
import nl.hauntedmc.featureframework.api.feature.FeatureCatalogListener;
import nl.hauntedmc.featureframework.api.feature.FeatureState;
import nl.hauntedmc.featureframework.api.service.CapabilityRef;
import nl.hauntedmc.featureframework.host.FeatureCollection;
import nl.hauntedmc.featureframework.host.FeatureDefinition;
import nl.hauntedmc.featureframework.velocity.host.VelocityFeature;
import nl.hauntedmc.featureframework.velocity.host.VelocityFeatureContext;
import nl.hauntedmc.featureframework.velocity.host.VelocityFeatureHost;

import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** Dummy Velocity plugin proving that one consumer artifact can host a collection of features. */
@Plugin(id = "featureframework-acceptance-velocity",
        name = "FeatureFramework Acceptance Velocity",
        version = "1.0.0")
public final class DummyVelocityPlugin {
    private final ProxyServer proxy;
    private final ComponentLogger logger;
    private final Path dataDirectory;
    private final AtomicInteger catalogTransitions = new AtomicInteger();
    private final AtomicBoolean observedStopping = new AtomicBoolean();
    private VelocityFeatureHost host;
    private AutoCloseable catalogSubscription;

    @Inject
    public DummyVelocityPlugin(
            ProxyServer proxy,
            ComponentLogger logger,
            @DataDirectory Path dataDirectory
    ) {
        this.proxy = proxy;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onInitialize(ProxyInitializeEvent event) {
        try {
            host = VelocityFeatureHost.builder(
                            this, proxy, logger, dataDirectory, DummyVelocityPlugin.class, features())
                    .hostName("FeatureFrameworkAcceptanceVelocity")
                    .version("1.0.0")
                    .capabilityNamespace("acceptance-velocity")
                    .build();
            FeatureCatalogListener listener = snapshot -> {
                catalogTransitions.incrementAndGet();
                if (snapshot.state() == FeatureState.STOPPING) observedStopping.set(true);
            };
            catalogSubscription = host.features().subscribe(listener);
            host.start();
            verifyInitialState();
        } catch (Throwable failure) {
            fail(failure);
        }
    }

    private void verifyInitialState() {
        require(host.state() == RuntimeState.READY, "Host did not become ready");
        require(host.features().snapshot().size() == 2, "Expected exactly two features");
        require(host.features().snapshot().stream()
                .allMatch(snapshot -> snapshot.state() == FeatureState.ACTIVE),
                "Not every feature became active");
        require(Provider.starts.get() == 1 && Consumer.starts.get() == 1,
                "Feature collection did not initialize once");
        require("velocity-1".equals(Consumer.lastGreeting),
                "Consumer did not resolve provider capability");

        CapabilityRef<GreetingApi> reference = host.capabilities().reference(GreetingApi.class);
        long generation = reference.generation().orElseThrow();
        logger.info("FEATUREFRAMEWORK_ACCEPTANCE_READY platform=velocity features=2");
        proxy.getScheduler().buildTask(this, () -> verifyReload(reference, generation))
                .delay(Duration.ofMillis(100))
                .schedule();
    }

    private void verifyReload(CapabilityRef<GreetingApi> reference, long initialGeneration) {
        try {
            require(host.reloadFeature("Provider").success(), "Provider graph reload failed");
            require(Provider.starts.get() == 2 && Consumer.starts.get() == 2,
                    "Reload did not recreate provider and dependent");
            require("velocity-2".equals(Consumer.lastGreeting),
                    "Dependent did not resolve the replacement provider");
            require(reference.generation().orElseThrow() > initialGeneration,
                    "Stable capability reference did not advance generation");
            require(observedStopping.get(), "FeatureCatalogListener did not observe reload shutdown");
            require(catalogTransitions.get() >= 12, "Too few public catalog transitions were observed");
            logger.info("FEATUREFRAMEWORK_ACCEPTANCE_PASS platform=velocity");
        } catch (Throwable failure) {
            fail(failure);
        }
    }

    @Subscribe
    public void onShutdown(ProxyShutdownEvent event) {
        Throwable failure = null;
        try {
            if (host != null) {
                host.stop();
                require(host.state() == RuntimeState.STOPPED, "Host did not stop");
            }
        } catch (Throwable shutdownFailure) {
            failure = shutdownFailure;
        } finally {
            closeSubscription();
        }
        if (failure == null) {
            logger.info("FEATUREFRAMEWORK_ACCEPTANCE_STOPPED platform=velocity");
        } else {
            fail(failure);
        }
    }

    private void closeSubscription() {
        if (catalogSubscription == null) return;
        try {
            catalogSubscription.close();
        } catch (Exception failure) {
            logger.warn("Could not close catalog subscription", failure);
        }
    }

    private void fail(Throwable failure) {
        logger.error("FEATUREFRAMEWORK_ACCEPTANCE_FAIL platform=velocity", failure);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private static FeatureCollection<VelocityFeature<Object, Void>, VelocityFeatureContext<Object, Void>> features() {
        FeatureDefinition<VelocityFeature<Object, Void>, VelocityFeatureContext<Object, Void>> consumer =
                FeatureDefinition.<VelocityFeature<Object, Void>, VelocityFeatureContext<Object, Void>>builder(
                                "Consumer", "1.0.0", Consumer.class, Consumer::new)
                        .requiresCapabilities(GreetingApi.class)
                        .enabledByDefault()
                        .build();
        FeatureDefinition<VelocityFeature<Object, Void>, VelocityFeatureContext<Object, Void>> provider =
                FeatureDefinition.<VelocityFeature<Object, Void>, VelocityFeatureContext<Object, Void>>builder(
                                "Provider", "1.0.0", Provider.class, Provider::new)
                        .providesCapabilities(GreetingApi.class)
                        .enabledByDefault()
                        .build();
        return FeatureCollection.<VelocityFeature<Object, Void>, VelocityFeatureContext<Object, Void>>builder()
                .feature(consumer)
                .feature(provider)
                .build();
    }

    /** Public contract published by the provider feature. */
    public interface GreetingApi {
        String greeting();
    }

    public static final class Provider extends VelocityFeature<Object, Void> {
        private static final AtomicInteger starts = new AtomicInteger();

        public Provider(VelocityFeatureContext<Object, Void> context) {
            super(context);
        }

        @Override
        public void initialize() {
            int generation = starts.incrementAndGet();
            getContext().services().registerService(GreetingApi.class, () -> "velocity-" + generation);
        }

        @Override public void disable() { }
    }

    public static final class Consumer extends VelocityFeature<Object, Void> {
        private static final AtomicInteger starts = new AtomicInteger();
        private static volatile String lastGreeting;

        public Consumer(VelocityFeatureContext<Object, Void> context) {
            super(context);
        }

        @Override
        public void initialize() {
            lastGreeting = requireCapability(GreetingApi.class).greeting();
            starts.incrementAndGet();
        }

        @Override public void disable() { }
    }
}
