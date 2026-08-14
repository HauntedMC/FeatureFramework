package nl.hauntedmc.featureframework.loader;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Executes the platform-neutral stop/start/rollback transaction for a feature graph reload.
 * Platform hosts supply graph discovery, state capture, and lifecycle operations only.
 */
public final class FeatureGraphReloadTransaction {
    private FeatureGraphReloadTransaction() {
    }

    public static <S> Result execute(
            String rootFeature,
            Supplier<List<String>> reloadOrder,
            Function<List<String>, Map<String, S>> captureStates,
            Function<List<String>, Throwable> stopGraph,
            BiPredicate<List<String>, Map<String, S>> startGraph
    ) {
        Objects.requireNonNull(rootFeature, "rootFeature");
        Objects.requireNonNull(reloadOrder, "reloadOrder");
        Objects.requireNonNull(captureStates, "captureStates");
        Objects.requireNonNull(stopGraph, "stopGraph");
        Objects.requireNonNull(startGraph, "startGraph");

        List<String> order;
        Map<String, S> states;
        try {
            order = List.copyOf(reloadOrder.get());
            states = Map.copyOf(captureStates.apply(order));
        } catch (Throwable failure) {
            return Result.failed(Stage.PREPARATION, List.of(), Set.of(), failure, null, false);
        }

        Set<String> dependents = new LinkedHashSet<>(order);
        dependents.remove(rootFeature);

        Throwable stopFailure = stopGraph.apply(order);
        if (stopFailure != null) {
            Rollback rollback = restore(order, states, stopGraph, startGraph);
            return Result.failed(
                    Stage.QUIESCE,
                    order,
                    dependents,
                    stopFailure,
                    rollback.cleanupFailure(),
                    rollback.success()
            );
        }

        if (startGraph.test(order, states)) {
            return Result.succeeded(order, dependents);
        }

        Throwable replacementCleanupFailure = stopGraph.apply(order);
        Rollback rollback = restore(order, states, stopGraph, startGraph);
        Throwable cleanupFailure = combine(replacementCleanupFailure, rollback.cleanupFailure());
        return Result.failed(Stage.START, order, dependents, null, cleanupFailure, rollback.success());
    }

    private static <S> Rollback restore(
            List<String> order,
            Map<String, S> states,
            Function<List<String>, Throwable> stopGraph,
            BiPredicate<List<String>, Map<String, S>> startGraph
    ) {
        if (startGraph.test(order, states)) return new Rollback(true, null);
        return new Rollback(false, stopGraph.apply(order));
    }

    private static Throwable combine(Throwable first, Throwable second) {
        if (first == null) return second;
        if (second != null) first.addSuppressed(second);
        return first;
    }

    public enum Stage {
        SUCCESS,
        PREPARATION,
        QUIESCE,
        START
    }

    public record Result(
            Stage stage,
            List<String> reloadOrder,
            Set<String> reloadedDependents,
            Throwable failure,
            Throwable cleanupFailure,
            boolean rollbackSucceeded
    ) {
        private static Result succeeded(List<String> order, Set<String> dependents) {
            return new Result(Stage.SUCCESS, List.copyOf(order), Set.copyOf(dependents), null, null, false);
        }

        private static Result failed(
                Stage stage,
                List<String> order,
                Set<String> dependents,
                Throwable failure,
                Throwable cleanupFailure,
                boolean rollbackSucceeded
        ) {
            return new Result(
                    stage,
                    List.copyOf(order),
                    Set.copyOf(dependents),
                    failure,
                    cleanupFailure,
                    rollbackSucceeded
            );
        }

        public boolean success() {
            return stage == Stage.SUCCESS;
        }
    }

    private record Rollback(boolean success, Throwable cleanupFailure) {
    }
}
