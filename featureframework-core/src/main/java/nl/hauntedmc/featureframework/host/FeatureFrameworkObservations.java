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
        return start(FeatureFrameworkOperationContext.host(kind));
    }

    Operation start(FeatureFrameworkOperationKind kind, FeatureId featureId) {
        return start(FeatureFrameworkOperationContext.feature(kind, featureId));
    }

    private Operation start(FeatureFrameworkOperationContext context) {
        if (!enabled) {
            return NOOP_OPERATION;
        }
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
}
