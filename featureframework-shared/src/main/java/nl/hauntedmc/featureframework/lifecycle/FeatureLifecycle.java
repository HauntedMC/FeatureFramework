package nl.hauntedmc.featureframework.lifecycle;

import java.util.List;
import java.util.Objects;

/**
 * Platform-neutral state machine for quiescing and releasing all resources owned by one feature.
 * Every step is attempted; the first failure is rethrown with later failures suppressed.
 */
public final class FeatureLifecycle {
    private final List<Runnable> quiesceSteps;
    private final List<Runnable> cleanupSteps;
    private FeatureResourceState state = FeatureResourceState.OPEN;

    public FeatureLifecycle(List<? extends Runnable> quiesceSteps, List<? extends Runnable> cleanupSteps) {
        this.quiesceSteps = copySteps(quiesceSteps, "quiesceSteps");
        this.cleanupSteps = copySteps(cleanupSteps, "cleanupSteps");
    }

    public synchronized FeatureResourceState state() {
        return state;
    }

    public synchronized void quiesce() {
        if (state != FeatureResourceState.OPEN) {
            return;
        }
        state = FeatureResourceState.QUIESCING;
        throwIfPresent(runSteps(null, quiesceSteps));
    }

    public synchronized void cleanup() {
        if (state == FeatureResourceState.CLOSED) {
            return;
        }
        Throwable failure = null;
        if (state == FeatureResourceState.OPEN) {
            state = FeatureResourceState.QUIESCING;
            failure = runSteps(null, quiesceSteps);
        }
        failure = runSteps(failure, cleanupSteps);
        state = FeatureResourceState.CLOSED;
        throwIfPresent(failure);
    }

    private static Throwable runSteps(Throwable failure, List<Runnable> steps) {
        for (Runnable step : steps) {
            try {
                step.run();
            } catch (Throwable stepFailure) {
                if (failure == null) {
                    failure = stepFailure;
                } else {
                    failure.addSuppressed(stepFailure);
                }
            }
        }
        return failure;
    }

    private static List<Runnable> copySteps(List<? extends Runnable> steps, String name) {
        Objects.requireNonNull(steps, name);
        return steps.stream().map(step -> Objects.requireNonNull(step, name + " element")).toList();
    }

    private static void throwIfPresent(Throwable failure) {
        if (failure != null) {
            throwUnchecked(failure);
        }
    }

    @SuppressWarnings("unchecked")
    private static <E extends Throwable> void throwUnchecked(Throwable throwable) throws E {
        throw (E) throwable;
    }
}
