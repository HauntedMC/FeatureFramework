package nl.hauntedmc.featureframework.acceptance.paper;

import nl.hauntedmc.featureframework.api.RuntimeState;
import nl.hauntedmc.featureframework.api.feature.FeatureCatalogListener;
import nl.hauntedmc.featureframework.api.feature.FeatureState;
import nl.hauntedmc.featureframework.api.service.CapabilityRef;
import nl.hauntedmc.featureframework.host.FeatureCollection;
import nl.hauntedmc.featureframework.host.FeatureDefinition;
import nl.hauntedmc.featureframework.paper.host.PaperFeature;
import nl.hauntedmc.featureframework.paper.host.PaperFeatureContext;
import nl.hauntedmc.featureframework.paper.host.PaperFeatureHost;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

/** Dummy Paper plugin proving that one consumer artifact can host a collection of features. */
public final class DummyPaperPlugin extends JavaPlugin {
    private final AtomicInteger catalogTransitions = new AtomicInteger();
    private final AtomicBoolean observedStopping = new AtomicBoolean();
    private PaperFeatureHost host;
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

        CapabilityRef<GreetingApi> reference = host.capabilities().reference(GreetingApi.class);
        long generation = reference.generation().orElseThrow();
        getLogger().info("FEATUREFRAMEWORK_ACCEPTANCE_READY platform=paper features=2");
        Bukkit.getScheduler().runTaskLaterAsynchronously(
                this, () -> verifyReload(reference, generation), 2L);
    }

    private void verifyReload(CapabilityRef<GreetingApi> reference, long initialGeneration) {
        try {
            require(!Bukkit.isPrimaryThread(), "Reload caller was expected to be asynchronous");
            require(host.reloadFeature("Provider").success(), "Provider graph reload failed");
            require(Provider.starts.get() == 2 && Consumer.starts.get() == 2,
                    "Reload did not recreate provider and dependent");
            require("paper-2".equals(Consumer.lastGreeting),
                    "Dependent did not resolve the replacement provider");
            require(reference.generation().orElseThrow() > initialGeneration,
                    "Stable capability reference did not advance generation");
            require(observedStopping.get(), "FeatureCatalogListener did not observe reload shutdown");
            require(catalogTransitions.get() >= 12, "Too few public catalog transitions were observed");
            getLogger().info("FEATUREFRAMEWORK_ACCEPTANCE_PASS platform=paper");
        } catch (Throwable failure) {
            fail(failure);
        }
    }

    @Override
    public void onDisable() {
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
        if (failure == null) getLogger().info("FEATUREFRAMEWORK_ACCEPTANCE_STOPPED platform=paper");
        else fail(failure);
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

    private static FeatureCollection<PaperFeature<Plugin, Void>, PaperFeatureContext<Plugin, Void>> features() {
        FeatureDefinition<PaperFeature<Plugin, Void>, PaperFeatureContext<Plugin, Void>> consumer =
                FeatureDefinition.<PaperFeature<Plugin, Void>, PaperFeatureContext<Plugin, Void>>builder(
                                "Consumer", "1.0.0", Consumer.class, Consumer::new)
                        .requiresCapabilities(GreetingApi.class)
                        .enabledByDefault()
                        .build();
        FeatureDefinition<PaperFeature<Plugin, Void>, PaperFeatureContext<Plugin, Void>> provider =
                FeatureDefinition.<PaperFeature<Plugin, Void>, PaperFeatureContext<Plugin, Void>>builder(
                                "Provider", "1.0.0", Provider.class, Provider::new)
                        .providesCapabilities(GreetingApi.class)
                        .enabledByDefault()
                        .build();
        return FeatureCollection.<PaperFeature<Plugin, Void>, PaperFeatureContext<Plugin, Void>>builder()
                .feature(consumer)
                .feature(provider)
                .build();
    }

    public interface GreetingApi { String greeting(); }

    public static final class Provider extends PaperFeature<Plugin, Void> {
        private static final AtomicInteger starts = new AtomicInteger();

        public Provider(PaperFeatureContext<Plugin, Void> context) { super(context); }

        @Override
        public void initialize() {
            requirePrimaryThread("Provider initialize");
            int generation = starts.incrementAndGet();
            getContext().services().registerService(GreetingApi.class, () -> "paper-" + generation);
        }

        @Override public void disable() { requirePrimaryThread("Provider disable"); }
    }

    public static final class Consumer extends PaperFeature<Plugin, Void> {
        private static final AtomicInteger starts = new AtomicInteger();
        private static volatile String lastGreeting;

        public Consumer(PaperFeatureContext<Plugin, Void> context) { super(context); }

        @Override
        public void initialize() {
            requirePrimaryThread("Consumer initialize");
            lastGreeting = requireCapability(GreetingApi.class).greeting();
            starts.incrementAndGet();
        }

        @Override public void disable() { requirePrimaryThread("Consumer disable"); }
    }
}
