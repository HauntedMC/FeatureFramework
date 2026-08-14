package nl.hauntedmc.featureframework.resource;

import nl.hauntedmc.featureframework.api.feature.FeatureId;
import nl.hauntedmc.featureframework.lifecycle.FeatureLifecycleResources;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FeatureResourceContributionPipelineTest {

    @Test
    void appliesOnlyContributorsRequestedByTheFeature() {
        TestResources resources = new TestResources();
        AtomicInteger requested = new AtomicInteger();
        AtomicInteger ignored = new AtomicInteger();
        FeatureResourceRequest request = request(Set.of(String.class), Set.of());

        TestResources result = FeatureResourceContributionPipeline.apply(
                request,
                List.of(contributor(String.class, value -> {
                    requested.incrementAndGet();
                    value.extensions.register(ResourceKey.of(String.class), "available");
                }), contributor(Integer.class, value -> ignored.incrementAndGet())),
                resources,
                resources.extensions
        );

        assertSame(resources, result);
        assertEquals(1, requested.get());
        assertEquals(0, ignored.get());
        assertEquals("available", resources.extensions.require(ResourceKey.of(String.class)));
        assertEquals(0, resources.cleanupCalls.get());
    }

    @Test
    void permitsAnUnavailableOptionalExtension() {
        TestResources resources = new TestResources();

        FeatureResourceContributionPipeline.apply(
                request(Set.of(), Set.of(Integer.class)), List.of(), resources, resources.extensions);

        assertEquals(0, resources.cleanupCalls.get());
    }

    @Test
    void cleansUpWhenARequiredExtensionWasNotContributed() {
        TestResources resources = new TestResources();

        IllegalStateException failure = assertThrows(IllegalStateException.class, () ->
                FeatureResourceContributionPipeline.apply(
                        request(Set.of(String.class), Set.of()), List.of(), resources, resources.extensions));

        assertEquals(1, resources.cleanupCalls.get());
        assertEquals("Feature activity requires resource extension java.lang.String, but its host did not contribute it",
                failure.getMessage());
    }

    @Test
    void preservesContributionFailureAndSuppressesCleanupFailure() {
        TestResources resources = new TestResources();
        IllegalStateException contributionFailure = new IllegalStateException("contribution failed");
        IllegalArgumentException cleanupFailure = new IllegalArgumentException("cleanup failed");
        resources.cleanupFailure = cleanupFailure;

        IllegalStateException thrown = assertThrows(IllegalStateException.class, () ->
                FeatureResourceContributionPipeline.apply(
                        request(Set.of(String.class), Set.of()),
                        List.of(contributor(String.class, ignored -> { throw contributionFailure; })),
                        resources,
                        resources.extensions));

        assertSame(contributionFailure, thrown);
        assertEquals(1, resources.cleanupCalls.get());
        assertEquals(List.of(cleanupFailure), List.of(thrown.getSuppressed()));
    }

    private static FeatureResourceRequest request(Set<Class<?>> required, Set<Class<?>> optional) {
        return new FeatureResourceRequest(FeatureId.of("activity"), "Activity", required, optional);
    }

    private static FeatureResourceContributor<TestResources> contributor(
            Class<?> extensionType,
            java.util.function.Consumer<TestResources> contribution
    ) {
        return new FeatureResourceContributor<>() {
            @Override public Class<?> extensionType() { return extensionType; }
            @Override public void contribute(FeatureResourceRequest request, TestResources resources) {
                contribution.accept(resources);
            }
        };
    }

    private static final class TestResources implements FeatureLifecycleResources {
        private final FeatureResourceExtensions extensions = new FeatureResourceExtensions();
        private final AtomicInteger cleanupCalls = new AtomicInteger();
        private RuntimeException cleanupFailure;

        @Override public void quiesce() { }

        @Override
        public void cleanup() {
            cleanupCalls.incrementAndGet();
            if (cleanupFailure != null) throw cleanupFailure;
        }
    }
}
