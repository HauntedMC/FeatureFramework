package nl.hauntedmc.featureframework.acceptance.velocity;

import com.google.inject.Inject;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyPingEvent;
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
import nl.hauntedmc.featureframework.lifecycle.FeatureResourceState;
import nl.hauntedmc.featureframework.velocity.command.brigadier.BrigadierCommand;
import nl.hauntedmc.featureframework.velocity.host.VelocityFeature;
import nl.hauntedmc.featureframework.velocity.host.VelocityFeatureContext;
import nl.hauntedmc.featureframework.velocity.host.VelocityFeatureHost;
import nl.hauntedmc.featureframework.velocity.lifecycle.VelocityFeatureResources;

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
        verifyOwnedResources(Provider.currentResources);

        CapabilityRef<GreetingApi> reference = host.capabilities().reference(GreetingApi.class);
        long generation = reference.generation().orElseThrow();
        logger.info("FEATUREFRAMEWORK_ACCEPTANCE_READY platform=velocity features=2");
        proxy.getScheduler().buildTask(this, () -> verifyReload(reference, generation))
                .delay(Duration.ofMillis(100))
                .schedule();
    }

    private void verifyReload(CapabilityRef<GreetingApi> reference, long initialGeneration) {
        VelocityFeatureResources<Void> previousResources = Provider.currentResources;
        Thread callerThread = Thread.currentThread();
        Provider.expectedLifecycleThread = callerThread;
        Consumer.expectedLifecycleThread = callerThread;
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
            verifyClosedResources(previousResources, "reload");
            require(Provider.currentResources != previousResources, "Reload reused the old resource scope");
            verifyOwnedResources(Provider.currentResources);
            logger.info("FEATUREFRAMEWORK_ACCEPTANCE_PASS platform=velocity");
        } catch (Throwable failure) {
            fail(failure);
        } finally {
            Provider.expectedLifecycleThread = null;
            Consumer.expectedLifecycleThread = null;
        }
    }

    @Subscribe
    public void onShutdown(ProxyShutdownEvent event) {
        Throwable failure = null;
        VelocityFeatureResources<Void> resources = Provider.currentResources;
        try {
            if (host != null) {
                host.stop();
                require(host.state() == RuntimeState.STOPPED, "Host did not stop");
                verifyClosedResources(resources, "host shutdown");
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

    private static void verifyOwnedResources(VelocityFeatureResources<Void> resources) {
        require(resources != null, "Provider resource scope was not captured");
        require(resources.state() == FeatureResourceState.OPEN, "Provider resource scope is not open");
        require(resources.getTaskManager().getActiveTaskCount() == 1, "Provider task was not tracked");
        require(resources.getListenerManager().getRegisteredListenerCount() == 1,
                "Provider listener was not tracked");
        require(resources.getCommandManager().getRegisteredBrigadierCommandCount() == 1,
                "Provider command was not tracked");
        require(resources.getApiManager().getRegisteredServiceCount() == 1,
                "Provider service was not tracked");
    }

    private static void verifyClosedResources(VelocityFeatureResources<Void> resources, String phase) {
        require(resources != null, "Missing resource scope during " + phase);
        require(resources.state() == FeatureResourceState.CLOSED, "Resource scope remained open after " + phase);
        require(resources.getTaskManager().state() == FeatureResourceState.CLOSED,
                "Task manager remained open after " + phase);
        require(resources.getTaskManager().getActiveTaskCount() == 0,
                "Tasks remained registered after " + phase);
        require(resources.getTaskManager().getInFlightTaskCount() == 0,
                "Tasks remained in flight after " + phase);
        require(resources.getListenerManager().state() == FeatureResourceState.CLOSED,
                "Listener manager remained open after " + phase);
        require(resources.getListenerManager().getRegisteredListenerCount() == 0,
                "Listeners remained registered after " + phase);
        require(resources.getCommandManager().state() == FeatureResourceState.CLOSED,
                "Command manager remained open after " + phase);
        require(resources.getCommandManager().getRegisteredBrigadierCommandCount() == 0,
                "Commands remained registered after " + phase);
        require(resources.getApiManager().state() == FeatureResourceState.CLOSED,
                "Service manager remained open after " + phase);
        require(resources.getApiManager().getRegisteredServiceCount() == 0,
                "Services remained registered after " + phase);
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

    private static void requireExpectedLifecycleThread(Thread expectedThread, String feature, String operation) {
        if (expectedThread != null) {
            require(Thread.currentThread() == expectedThread,
                    feature + " " + operation + " was moved off the Velocity lifecycle caller thread");
        }
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
        private static volatile VelocityFeatureResources<Void> currentResources;
        private static volatile Thread expectedLifecycleThread;

        public Provider(VelocityFeatureContext<Object, Void> context) {
            super(context);
        }

        @Override
        public void initialize() {
            requireExpectedLifecycleThread(expectedLifecycleThread, "Provider", "initialize");
            int generation = starts.incrementAndGet();
            currentResources = getContext().resources();
            currentResources.getTaskManager().scheduleRepeatingTask(
                    () -> { }, Duration.ofSeconds(10), Duration.ofSeconds(10));
            currentResources.getListenerManager().registerListener(new AcceptanceListener());
            currentResources.getCommandManager().registerBrigadierCommand(new AcceptanceCommand());
            getContext().services().registerService(GreetingApi.class, () -> "velocity-" + generation);
        }

        @Override
        public void disable() {
            requireExpectedLifecycleThread(expectedLifecycleThread, "Provider", "disable");
        }
    }

    public static final class Consumer extends VelocityFeature<Object, Void> {
        private static final AtomicInteger starts = new AtomicInteger();
        private static volatile String lastGreeting;
        private static volatile Thread expectedLifecycleThread;

        public Consumer(VelocityFeatureContext<Object, Void> context) {
            super(context);
        }

        @Override
        public void initialize() {
            requireExpectedLifecycleThread(expectedLifecycleThread, "Consumer", "initialize");
            lastGreeting = requireCapability(GreetingApi.class).greeting();
            starts.incrementAndGet();
        }

        @Override
        public void disable() {
            requireExpectedLifecycleThread(expectedLifecycleThread, "Consumer", "disable");
        }
    }

    public static final class AcceptanceListener {
        @Subscribe
        public void onPing(ProxyPingEvent event) {
            // Registration ownership is the behavior under test; no event mutation is required.
        }
    }

    public static final class AcceptanceCommand implements BrigadierCommand {
        @Override public String name() { return "ffvelocityaccept"; }

        @Override
        public com.mojang.brigadier.tree.LiteralCommandNode<CommandSource> buildTree() {
            return LiteralArgumentBuilder.<CommandSource>literal(name())
                    .executes(context -> 1)
                    .build();
        }
    }
}
