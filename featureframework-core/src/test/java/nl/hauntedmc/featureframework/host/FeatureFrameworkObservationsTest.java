package nl.hauntedmc.featureframework.host;

import nl.hauntedmc.featureframework.api.feature.FeatureId;
import nl.hauntedmc.featureframework.api.observation.FeatureFrameworkObservation;
import nl.hauntedmc.featureframework.api.observation.FeatureFrameworkObservationScope;
import nl.hauntedmc.featureframework.api.observation.FeatureFrameworkOperationContext;
import nl.hauntedmc.featureframework.api.observation.FeatureFrameworkOperationKind;
import nl.hauntedmc.featureframework.api.observation.FeatureFrameworkOperationOutcome;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeatureFrameworkObservationsTest {

    @Test
    void contextContainsOnlyBoundedOperationAndOptionalFeatureId() {
        List<String> components = Arrays.stream(FeatureFrameworkOperationContext.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();
        assertEquals(List.of("operation", "featureId"), components);

        var host = FeatureFrameworkOperationContext.host(FeatureFrameworkOperationKind.HOST_START);
        assertTrue(host.featureId().isEmpty());

        FeatureId featureId = FeatureId.of("lottery");
        var feature = FeatureFrameworkOperationContext.feature(
                FeatureFrameworkOperationKind.FEATURE_LOAD,
                featureId
        );
        assertEquals(featureId, feature.featureId().orElseThrow());

        assertThrows(IllegalArgumentException.class, () -> new FeatureFrameworkOperationContext(
                FeatureFrameworkOperationKind.FEATURE_LOAD,
                java.util.Optional.empty()
        ));
        assertThrows(IllegalArgumentException.class, () -> new FeatureFrameworkOperationContext(
                FeatureFrameworkOperationKind.HOST_STOP,
                java.util.Optional.of(featureId)
        ));
    }

    @Test
    void observerFailuresAndScopeFailuresCannotChangeFrameworkWork() {
        AtomicInteger starts = new AtomicInteger();
        AtomicInteger completions = new AtomicInteger();
        AtomicBoolean scopeClosed = new AtomicBoolean();
        FeatureFrameworkObservations observations = new FeatureFrameworkObservations(context -> {
            starts.incrementAndGet();
            return new FeatureFrameworkObservation() {
                @Override
                public FeatureFrameworkObservationScope openScope() {
                    return () -> {
                        scopeClosed.set(true);
                        throw new IllegalStateException("scope close failure");
                    };
                }

                @Override
                public void completed(FeatureFrameworkOperationOutcome outcome, Throwable failure) {
                    completions.incrementAndGet();
                    throw new IllegalStateException("completion failure");
                }
            };
        });

        assertEquals("ok", observations.observe(
                FeatureFrameworkOperationKind.FEATURE_ENABLE,
                FeatureId.of("lottery"),
                () -> "ok",
                ignored -> FeatureFrameworkOperationOutcome.SUCCESS,
                ignored -> null
        ));
        assertEquals(1, starts.get());
        assertEquals(1, completions.get());
        assertTrue(scopeClosed.get());
    }

    @Test
    void observationCompletesExactlyOnce() {
        AtomicInteger completions = new AtomicInteger();
        FeatureFrameworkObservations observations = new FeatureFrameworkObservations(context ->
                recordingObservation(new AtomicReference<>(), completions));

        FeatureFrameworkObservations.Operation operation = observations.start(
                FeatureFrameworkOperationKind.FEATURE_LOAD,
                FeatureId.of("lottery")
        );
        operation.complete(FeatureFrameworkOperationOutcome.SUCCESS, null);
        operation.complete(FeatureFrameworkOperationOutcome.FAILURE, new IllegalStateException());

        assertEquals(1, completions.get());
    }

    @Test
    void observerStartFailureFallsBackToNoop() {
        FeatureFrameworkObservations observations = new FeatureFrameworkObservations(context -> {
            throw new IllegalStateException("adapter failure");
        });

        assertEquals(42, observations.observe(
                FeatureFrameworkOperationKind.GRAPH_RELOAD,
                () -> 42,
                ignored -> FeatureFrameworkOperationOutcome.SUCCESS,
                ignored -> null
        ));
    }

    @Test
    void disabledAndFilteredObserversSkipTerminalClassification() {
        FeatureFrameworkObservations disabled = new FeatureFrameworkObservations(
                nl.hauntedmc.featureframework.api.observation.FeatureFrameworkObserver.noop());
        FeatureFrameworkObservations filtered = new FeatureFrameworkObservations(
                context -> FeatureFrameworkObservation.noop());

        assertEquals("disabled", disabled.observe(
                FeatureFrameworkOperationKind.GRAPH_RELOAD,
                () -> "disabled",
                ignored -> { throw new AssertionError("disabled operation was classified"); },
                ignored -> { throw new AssertionError("disabled failure was inspected"); }
        ));
        assertEquals("filtered", filtered.observe(
                FeatureFrameworkOperationKind.GRAPH_RELOAD,
                () -> "filtered",
                ignored -> { throw new AssertionError("filtered operation was classified"); },
                ignored -> { throw new AssertionError("filtered failure was inspected"); }
        ));
    }

    @Test
    void classificationFailureCannotChangeFrameworkWork() {
        AtomicReference<FeatureFrameworkOperationOutcome> outcome = new AtomicReference<>();
        AtomicReference<Throwable> observedFailure = new AtomicReference<>();
        FeatureFrameworkObservations observations = new FeatureFrameworkObservations(context ->
                new FeatureFrameworkObservation() {
                    @Override
                    public void completed(FeatureFrameworkOperationOutcome value, Throwable failure) {
                        outcome.set(value);
                        observedFailure.set(failure);
                    }
                });

        assertEquals("ok", observations.observe(
                FeatureFrameworkOperationKind.GRAPH_RELOAD,
                () -> "ok",
                ignored -> { throw new IllegalStateException("classification failure"); },
                ignored -> null
        ));
        assertEquals(FeatureFrameworkOperationOutcome.FAILURE, outcome.get());
        assertEquals("classification failure", observedFailure.get().getMessage());
    }

    @Test
    void scopeIsActiveAroundActualWork() {
        AtomicBoolean active = new AtomicBoolean();
        AtomicBoolean closed = new AtomicBoolean();
        FeatureFrameworkObservations observations = new FeatureFrameworkObservations(context ->
                new FeatureFrameworkObservation() {
                    @Override
                    public FeatureFrameworkObservationScope openScope() {
                        active.set(true);
                        return () -> {
                            active.set(false);
                            closed.set(true);
                        };
                    }

                    @Override
                    public void completed(FeatureFrameworkOperationOutcome outcome, Throwable failure) {
                    }
                });

        assertTrue(observations.observe(
                FeatureFrameworkOperationKind.HOST_START,
                active::get,
                ignored -> FeatureFrameworkOperationOutcome.SUCCESS,
                ignored -> null
        ));
        assertFalse(active.get());
        assertTrue(closed.get());
    }

    @Test
    void workFailureIsReportedAndRethrownUnchanged() {
        AtomicReference<FeatureFrameworkOperationOutcome> outcome = new AtomicReference<>();
        AtomicReference<Throwable> observedFailure = new AtomicReference<>();
        FeatureFrameworkObservations observations = new FeatureFrameworkObservations(context ->
                new FeatureFrameworkObservation() {
                    @Override
                    public void completed(FeatureFrameworkOperationOutcome value, Throwable failure) {
                        outcome.set(value);
                        observedFailure.set(failure);
                    }
                });
        IllegalStateException failure = new IllegalStateException("boom");

        IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> observations.observe(
                FeatureFrameworkOperationKind.FEATURE_RECREATE,
                FeatureId.of("lottery"),
                () -> { throw failure; },
                ignored -> FeatureFrameworkOperationOutcome.SUCCESS,
                ignored -> null
        ));

        assertTrue(thrown == failure);
        assertEquals(FeatureFrameworkOperationOutcome.FAILURE, outcome.get());
        assertTrue(observedFailure.get() == failure);
    }

    private static FeatureFrameworkObservation recordingObservation(
            AtomicReference<FeatureFrameworkOperationOutcome> outcome,
            AtomicInteger completions
    ) {
        return new FeatureFrameworkObservation() {
            @Override
            public void completed(FeatureFrameworkOperationOutcome value, Throwable failure) {
                outcome.set(value);
                completions.incrementAndGet();
            }
        };
    }
}
