package nl.hauntedmc.featureframework.host;

import nl.hauntedmc.featureframework.api.RuntimeState;
import nl.hauntedmc.featureframework.api.feature.ActivationDecision;
import nl.hauntedmc.featureframework.api.feature.FeatureId;
import nl.hauntedmc.featureframework.api.feature.FeaturePlacement;
import nl.hauntedmc.featureframework.api.feature.FeatureState;
import nl.hauntedmc.featureframework.api.feature.FeatureSuppressionReason;
import nl.hauntedmc.featureframework.config.DefaultFeatureConfiguration;
import nl.hauntedmc.featureframework.feature.LifecycleFeature;
import nl.hauntedmc.featureframework.runtime.FeatureRuntime;
import nl.hauntedmc.featureframework.service.DefaultCapabilityRegistry;
import nl.hauntedmc.featureframework.toolkit.io.config.ConfigMap;
import nl.hauntedmc.featureframework.toolkit.io.config.ConfigService;
import nl.hauntedmc.featureframework.toolkit.io.localization.MessageMap;
import nl.hauntedmc.featureframework.toolkit.log.FrameworkLogger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FeaturePlacementSuppressionTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void suppressedPlacementNeverConstructsContextFeatureOrResources() {
        FrameworkLogger logger = FrameworkLogger.noop();
        ConfigService files = new ConfigService(temporaryDirectory, logger, getClass().getClassLoader());
        DefaultFeatureConfiguration configuration = new DefaultFeatureConfiguration(files, logger);
        DefaultCapabilityRegistry capabilities = new DefaultCapabilityRegistry(
                getClass().getPackageName(), getClass().getClassLoader());
        FeatureRuntime<FeatureId, DefaultCapabilityRegistry> runtime =
                new FeatureRuntime<>("suppressed-host", capabilities);
        AtomicInteger contextConstructions = new AtomicInteger();
        AtomicInteger featureConstructions = new AtomicInteger();

        FeatureDefinition<SuppressedFeature, SuppressedContext> definition =
                FeatureDefinition.<SuppressedFeature, SuppressedContext>builder(
                                "Ingress", "1.0.0", SuppressedFeature.class,
                                context -> {
                                    featureConstructions.incrementAndGet();
                                    return new SuppressedFeature(context);
                                })
                        .placement(FeaturePlacement.GROUP_LEADER_ONLY)
                        .enabledByDefault()
                        .build();

        FeatureHost<String, SuppressedFeature, SuppressedContext> host = FeatureHost.builder(
                        "suppressed-host", "1.0.0", "test", runtime, configuration,
                        FeatureCollection.of(definition))
                .contextFactory(descriptor -> {
                    contextConstructions.incrementAndGet();
                    throw new AssertionError("suppressed feature requested a runtime context/resource scope");
                })
                .activationPolicy((metadata, phase) -> ActivationDecision.suppress(
                        FeatureSuppressionReason.GROUP_LEADER_ONLY,
                        "Follower is not eligible for leader-only placement"))
                .logger(logger)
                .build();

        host.start();

        assertEquals(RuntimeState.READY, host.state());
        assertEquals(0, contextConstructions.get());
        assertEquals(0, featureConstructions.get());
        var snapshot = host.features().find(FeatureId.of("Ingress")).orElseThrow();
        assertEquals(FeatureState.SUPPRESSED, snapshot.state());
        assertEquals(FeatureSuppressionReason.GROUP_LEADER_ONLY,
                snapshot.suppression().orElseThrow().reason());
        host.stop();
    }

    private interface SuppressedContext extends FeatureHostContext { }

    private static final class SuppressedFeature extends LifecycleFeature<SuppressedContext> {
        private SuppressedFeature(SuppressedContext context) {
            super(context);
        }

        @Override public ConfigMap defaultConfig() { return new ConfigMap(); }
        @Override public MessageMap defaultMessages() { return new MessageMap(); }
        @Override public void initialize() { }
        @Override public void disable() { }
    }
}