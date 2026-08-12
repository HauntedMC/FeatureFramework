package nl.hauntedmc.featureframework.spi.lifecycle;

import nl.hauntedmc.featureframework.lifecycle.FeatureResourceState;
import nl.hauntedmc.featureframework.lifecycle.FeatureTaskTracker;

import java.time.Duration;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Shared lifecycle facade for platform-native scheduled task handles.
 *
 * <p>Paper and Velocity keep their own native scheduling APIs. This class owns only registration,
 * quiescing, cancellation, in-flight accounting, and drain semantics so those rules cannot drift
 * between adapters.</p>
 *
 * @param <H> native platform task handle
 */
public final class TaskLifecycleCore<H> {
    private final FeatureTaskTracker<H> tracker = new FeatureTaskTracker<>();

    public H scheduleOnce(Function<Runnable, H> submitter, Runnable task, Consumer<H> cancellation) {
        return tracker.scheduleOnce(
                Objects.requireNonNull(submitter, "submitter"),
                Objects.requireNonNull(task, "task"),
                Objects.requireNonNull(cancellation, "cancellation"));
    }

    public H scheduleRepeating(Function<Runnable, H> submitter, Runnable task, Consumer<H> cancellation) {
        return tracker.scheduleRepeating(
                Objects.requireNonNull(submitter, "submitter"),
                Objects.requireNonNull(task, "task"),
                Objects.requireNonNull(cancellation, "cancellation"));
    }

    public void cancel(H handle, Consumer<H> cancellation) {
        tracker.cancel(handle, Objects.requireNonNull(cancellation, "cancellation"));
    }

    public void quiesce() {
        tracker.quiesce();
    }

    public void cancelAll(Consumer<H> cancellation) {
        tracker.cancelAll(Objects.requireNonNull(cancellation, "cancellation"));
    }

    public void cancelAll(Consumer<H> cancellation, Duration timeout) {
        tracker.cancelAll(
                Objects.requireNonNull(cancellation, "cancellation"),
                Objects.requireNonNull(timeout, "timeout"));
    }

    public FeatureResourceState state() {
        return tracker.state();
    }

    public int activeCount() {
        return tracker.activeCount();
    }

    public int inFlightCount() {
        return tracker.inFlightCount();
    }
}
