package nl.hauntedmc.featureframework.acceptance.paper;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import nl.hauntedmc.featureframework.api.RuntimeState;
import nl.hauntedmc.featureframework.api.feature.FeatureCatalogListener;
import nl.hauntedmc.featureframework.api.feature.FeatureId;
import nl.hauntedmc.featureframework.api.feature.FeatureState;
import nl.hauntedmc.featureframework.api.service.CapabilityRef;
import nl.hauntedmc.featureframework.host.FeatureCollection;
import nl.hauntedmc.featureframework.host.FeatureDefinition;
import nl.hauntedmc.featureframework.lifecycle.FeatureResourceState;
import nl.hauntedmc.featureframework.paper.command.brigadier.BrigadierCommand;
import nl.hauntedmc.featureframework.paper.host.PaperFeature;
import nl.hauntedmc.featureframework.paper.host.PaperFeatureContext;
import nl.hauntedmc.featureframework.paper.host.PaperFeatureHost;
import nl.hauntedmc.featureframework.paper.lifecycle.PaperFeatureResources;
import nl.hauntedmc.featureframework.paper.time.BukkitTime;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

/** Dummy Paper plugin proving that one consumer artifact can host a collection of features. */
public final class DummyPaperPlugin extends JavaPlugin {
    private final AtomicInteger catalogTransitions = new AtomicInteger();
    private final AtomicBoolean observedStopping = new AtomicBoolean();
    private PaperFeatureHost<DummyPaperPlugin, String> host;
    private AutoCloseable catalogSubscription;

    @Override
    public void onEnable() {
        try {
            host = PaperFeatureHost.builder(this, DummyPaperPlugin.class, features())
                    .capabilityNamespace("acceptance-paper")
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
        require("paper-1".equals(Consumer.lastGreeting), "Consumer did not resolve provider capability");
        verifyOwnedResources(Provider.currentResources);

        CapabilityRef<GreetingApi> reference = host.capabilities().reference(GreetingApi.class);
        long generation = reference.generation().orElseThrow();
        getLogger().info("FEATUREFRAMEWORK_ACCEPTANCE_READY platform=paper features=2");
        Bukkit.getScheduler().runTaskLaterAsynchronously(
                this, () -> verifyReload(reference, generation), 2L);
    }

    private void verifyReload(CapabilityRef<GreetingApi> reference, long initialGeneration) {
        PaperFeatureResources previousResources = Provider.currentResources;
        try {
            require(!Bukkit.isPrimaryThread(), "Reload caller was expected to be asynchronous");
            require(host.recreate(FeatureId.of("Provider")).success(), "Provider graph reload failed");
            require(Provider.starts.get() == 2 && Consumer.starts.get() == 2,
                    "Reload did not recreate provider and dependent");
            require("paper-2".equals(Consumer.lastGreeting),
                    "Dependent did not resolve the replacement provider");
            require(reference.generation().orElseThrow() > initialGeneration,
                    "Stable capability reference did not advance generation");
            require(observedStopping.get(), "FeatureCatalogListener did not observe reload shutdown");
            require(catalogTransitions.get() >= 12, "Too few public catalog transitions were observed");
            verifyClosedResources(previousResources, "reload");
            require(Provider.currentResources != previousResources, "Reload reused the old resource scope");
            verifyOwnedResources(Provider.currentResources);
            getLogger().info("FEATUREFRAMEWORK_ACCEPTANCE_PASS platform=paper");
        } catch (Throwable failure) {
            fail(failure);
        }
    }

    @Override
    public void onDisable() {
        Throwable failure = null;
        PaperFeatureResources resources = Provider.currentResources;
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
        if (failure == null) getLogger().info("FEATUREFRAMEWORK_ACCEPTANCE_STOPPED platform=paper");
        else fail(failure);
    }

    private static void verifyOwnedResources(PaperFeatureResources resources) {
        require(resources != null, "Provider resource scope was not captured");
        require(resources.state() == FeatureResourceState.OPEN, "Provider resource scope is not open");
        require(resources.tasks().getActiveTaskCount() == 1, "Provider task was not tracked");
        require(resources.listeners().getRegisteredListenerCount() == 1,
                "Provider listener was not tracked");
        require(resources.commands().getRegisteredBrigadierCommandCount() == 1,
                "Provider command was not tracked");
        require(resources.serviceManager().getRegisteredServiceCount() == 1,
                "Provider service was not tracked");
    }

    private static void verifyClosedResources(PaperFeatureResources resources, String phase) {
        require(resources != null, "Missing resource scope during " + phase);
        require(resources.state() == FeatureResourceState.CLOSED, "Resource scope remained open after " + phase);
        require(resources.tasks().state() == FeatureResourceState.CLOSED,
                "Task manager remained open after " + phase);
        require(resources.tasks().getActiveTaskCount() == 0,
                "Tasks remained registered after " + phase);
        require(resources.tasks().getInFlightTaskCount() == 0,
                "Tasks remained in flight after " + phase);
        require(resources.listeners().state() == FeatureResourceState.CLOSED,
                "Listener manager remained open after " + phase);
        require(resources.listeners().getRegisteredListenerCount() == 0,
                "Listeners remained registered after " + phase);
        require(resources.commands().state() == FeatureResourceState.CLOSED,
                "Command manager remained open after " + phase);
        require(resources.commands().getRegisteredBrigadierCommandCount() == 0,
                "Commands remained registered after " + phase);
        require(resources.serviceManager().state() == FeatureResourceState.CLOSED,
                "Service manager remained open after " + phase);
        require(resources.serviceManager().getRegisteredServiceCount() == 0,
                "Services remained registered after " + phase);
    }

    private void closeSubscription() {
        if (catalogSubscription == null) return;
        try {
            catalogSubscription.close();
        } catch (Exception failure) {
            getLogger().log(Level.WARNING, "Could not close catalog subscription", failure);
        }
    }

    private void fail(Throwable failure) {
        getLogger().log(Level.SEVERE, "FEATUREFRAMEWORK_ACCEPTANCE_FAIL platform=paper", failure);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private static void requirePrimaryThread(String phase) {
        require(Bukkit.isPrimaryThread(), phase + " did not run on Paper's primary thread");
    }

    private static FeatureCollection<PaperFeature<DummyPaperPlugin>, PaperFeatureContext<DummyPaperPlugin>> features() {
        FeatureDefinition<PaperFeature<DummyPaperPlugin>, PaperFeatureContext<DummyPaperPlugin>> consumer =
                FeatureDefinition.<PaperFeature<DummyPaperPlugin>, PaperFeatureContext<DummyPaperPlugin>>builder(
                                "Consumer", "1.0.0", Consumer.class, Consumer::new)
                        .requiresCapabilities(GreetingApi.class)
                        .enabledByDefault()
                        .build();
        FeatureDefinition<PaperFeature<DummyPaperPlugin>, PaperFeatureContext<DummyPaperPlugin>> provider =
                FeatureDefinition.<PaperFeature<DummyPaperPlugin>, PaperFeatureContext<DummyPaperPlugin>>builder(
                                "Provider", "1.0.0", Provider.class, Provider::new)
                        .providesCapabilities(GreetingApi.class)
                        .enabledByDefault()
                        .build();
        return FeatureCollection.<PaperFeature<DummyPaperPlugin>, PaperFeatureContext<DummyPaperPlugin>>builder()
                .feature(consumer)
                .feature(provider)
                .build();
    }

    public interface GreetingApi { String greeting(); }

    public static final class Provider extends PaperFeature<DummyPaperPlugin> {
        private static final AtomicInteger starts = new AtomicInteger();
        private static volatile PaperFeatureResources currentResources;

        public Provider(PaperFeatureContext<DummyPaperPlugin> context) { super(context); }

        @Override
        public void initialize() {
            requirePrimaryThread("Provider initialize");
            int generation = starts.incrementAndGet();
            currentResources = context().resources();
            currentResources.tasks().scheduleRepeatingTask(
                    () -> { }, BukkitTime.ticks(200L), BukkitTime.ticks(200L));
            currentResources.listeners().registerListener(new AcceptanceListener());
            currentResources.commands().registerBrigadierCommand(new AcceptanceCommand());
            services().publish(GreetingApi.class, () -> "paper-" + generation);
        }

        @Override public void disable() { requirePrimaryThread("Provider disable"); }
    }

    public static final class Consumer extends PaperFeature<DummyPaperPlugin> {
        private static final AtomicInteger starts = new AtomicInteger();
        private static volatile String lastGreeting;

        public Consumer(PaperFeatureContext<DummyPaperPlugin> context) { super(context); }

        @Override
        public void initialize() {
            requirePrimaryThread("Consumer initialize");
            lastGreeting = services().require(GreetingApi.class).greeting();
            starts.incrementAndGet();
        }

        @Override public void disable() { requirePrimaryThread("Consumer disable"); }
    }

    public static final class AcceptanceListener implements Listener {
        @EventHandler
        public void onJoin(PlayerJoinEvent event) {
            // Registration ownership is the behavior under test; no event mutation is required.
        }
    }

    public static final class AcceptanceCommand implements BrigadierCommand {
        @Override public String name() { return "ffpaperaccept"; }

        @Override
        public com.mojang.brigadier.tree.LiteralCommandNode<CommandSourceStack> buildTree() {
            return LiteralArgumentBuilder.<CommandSourceStack>literal(name())
                    .executes(context -> 1)
                    .build();
        }
    }
}
