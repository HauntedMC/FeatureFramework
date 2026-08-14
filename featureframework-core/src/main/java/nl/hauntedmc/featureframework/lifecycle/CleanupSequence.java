package nl.hauntedmc.featureframework.lifecycle;

import java.util.List;
import java.util.Objects;

/** Executes every cleanup step and rethrows the first failure with later failures suppressed. */
public final class CleanupSequence {
    private CleanupSequence() { }

    public static void run(Runnable... steps) {
        run(List.of(steps));
    }

    public static void run(List<? extends Runnable> steps) {
        Objects.requireNonNull(steps, "steps");
        Throwable failure = null;
        for (Runnable step : steps) {
            try {
                Objects.requireNonNull(step, "step").run();
            } catch (Throwable addition) {
                if (failure == null) failure = addition;
                else failure.addSuppressed(addition);
            }
        }
        if (failure != null) throwUnchecked(failure);
    }

    @SuppressWarnings("unchecked")
    private static <E extends Throwable> void throwUnchecked(Throwable failure) throws E { throw (E) failure; }
}
