package nl.hauntedmc.featureframework.host;

import nl.hauntedmc.featureframework.api.feature.FeatureId;
import nl.hauntedmc.featureframework.api.observation.FeatureFrameworkObservation;
import nl.hauntedmc.featureframework.api.observation.FeatureFrameworkObservationScope;
import nl.hauntedmc.featureframework.api.observation.FeatureFrameworkObserver;
import nl.hauntedmc.featureframework.api.observation.FeatureFrameworkOperationContext;
import nl.hauntedmc.featureframework.api.observation.FeatureFrameworkOperationKind;
import nl.hauntedmc.featureframework.api.observation.FeatureFrameworkOperationOutcome;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Supplier;

/** Runtime-local, single-observer dispatcher that keeps instrumentation fail-open. */
final class FeatureFrameworkObservations {

    private static final Operation NOOP_OPERATION = new Operation(FeatureFrameworkObservation.noop());

    private final FeatureFrameworkObserver observer;
    private final boolean enabled;

    FeatureFrameworkObservations(FeatureFrameworkObserver observer) {
        this.observer = Objects.requireNonNull(observer, "observer");
        this.enabled = observer != FeatureFrameworkObserver.noop();
    }

    boolean isEnabled() {
        return enabled;
    }

    Operation start(FeatureFrameworkOperationKind kind) {
        if (!enabled) {
            return NOOP_OPERATION;
        }
        return start(FeatureFrameworkOperationContext.host(kind));
    }

    Operation start(FeatureFrameworkOperationKind kind, FeatureId featureId) {
        if (!enabled) {
            return NOOP_OPERATION;
        }
        return start(FeatureFrameworkOperationContext.feature(kind, featureId));
    }

    <T> T observe(
            FeatureFrameworkOperationKind kind,
            Supplier<T> work,
            Function<T, FeatureFrameworkOperationOutcome> outcome,
            Function<T, Throwable> failure
    ) {
        return observe(start(kind), work, outcome, failure);
    }

    <T> T observe(
            FeatureFrameworkOperationKind kind,
            FeatureId featureId,
            Supplier<T> work,
            Function<T, FeatureFrameworkOperationOutcome> outcome,
            Function<T, Throwable> failure
    ) {
        return observe(start(kind, featureId), work, outcome, failure);
    }

    private <T> T observe(
            Operation operation,
            Supplier<T> work,
            Function<T, FeatureFrameworkOperationOutcome> outcome,
            Function<T, Throwable> failure
    ) {
        Objects.requireNonNull(work, "work");
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(failure, "failure");
        FeatureFrameworkObservationScope scope = operation.openScope();
        try {
            try {
                T result = work.get();
                operation.complete(
                        Objects.requireNonNull(outcome.apply(result), "observation outcome"),
                        failure.apply(result)
                );
                return result;
            } catch (Throwable throwable) {
                operation.complete(FeatureFrameworkOperationOutcome.FAILURE, throwable);
                return throwUnchecked(throwable);
            }
        } finally {
            scope.close();
        }
    }

    private Operation start(FeatureFrameworkOperationContext context) {
        try {
            FeatureFrameworkObservation observation = observer.start(context);
            if (observation == null || observation == FeatureFrameworkObservation.noop()) {
                return NOOP_OPERATION;
            }
            return new Operation(observation);
        } catch (RuntimeException ignored) {
            return NOOP_OPERATION;
        }
    }

    static final class Operation {
        private final FeatureFrameworkObservation observation;
        private final AtomicBoolean completed;

        private Operation(FeatureFrameworkObservation observation) {
            this.observation = observation;
            this.completed = observation == FeatureFrameworkObservation.noop() ? null : new AtomicBoolean();
        }

        FeatureFrameworkObservationScope openScope() {
            if (completed == null) {
                return FeatureFrameworkObservationScope.noop();
            }
            try {
                FeatureFrameworkObservationScope scope = observation.openScope();
                if (scope == null || scope == FeatureFrameworkObservationScope.noop()) {
                    return FeatureFrameworkObservationScope.noop();
                }
                return new SafeScope(scope);
            } catch (RuntimeException ignored) {
                return FeatureFrameworkObservationScope.noop();
            }
        }

        void complete(FeatureFrameworkOperationOutcome outcome, Throwable failure) {
            Objects.requireNonNull(outcome, "outcome");
            if (completed == null || !completed.compareAndSet(false, true)) {
                return;
            }
            try {
                observation.completed(outcome, failure);
            } catch (RuntimeException ignored) {
                // Instrumentation must never replace the framework operation outcome.
            }
        }
    }

    private static final class SafeScope implements FeatureFrameworkObservationScope {
        private final FeatureFrameworkObservationScope delegate;
        private final AtomicBoolean closed = new AtomicBoolean();

        private SafeScope(FeatureFrameworkObservationScope delegate) {
            this.delegate = delegate;
        }

        @Override
        public void close() {
            if (!closed.compareAndSet(false, true)) {
                return;
            }
            try {
                delegate.close();
            } catch (RuntimeException ignored) {
                // Context cleanup must never alter FeatureFramework behavior.
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static <T, E extends Throwable> T throwUnchecked(Throwable failure) throws E {
        throw (E) failure;
    }
}
