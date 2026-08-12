package nl.hauntedmc.featureframework.lifecycle;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Resolves asynchronous readiness and safely transfers successful work to an owned scheduler. */
public final class AsyncReadinessGate {
    private AsyncReadinessGate() { }

    public static <V> void runWhenReady(
            Supplier<? extends CompletionStage<Optional<V>>> lookup,
            Consumer<Runnable> scheduler,
            Consumer<V> readyAction,
            Consumer<String> warningLogger,
            String operationName
    ) {
        Objects.requireNonNull(lookup, "lookup");
        Objects.requireNonNull(scheduler, "scheduler");
        Objects.requireNonNull(readyAction, "readyAction");
        Objects.requireNonNull(warningLogger, "warningLogger");
        Objects.requireNonNull(operationName, "operationName");

        CompletionStage<Optional<V>> readiness;
        try {
            readiness = Objects.requireNonNull(lookup.get(), "Readiness lookup returned null");
        } catch (RuntimeException failure) {
            warningLogger.accept("Could not start readiness lookup for " + operationName + ": " + rootMessage(failure));
            return;
        }

        readiness.whenComplete((value, failure) -> {
            if (failure != null) {
                warningLogger.accept("Readiness was unavailable for " + operationName + ": " + rootMessage(failure));
                return;
            }
            if (value == null || value.isEmpty()) {
                return;
            }
            try {
                scheduler.accept(() -> readyAction.accept(value.get()));
            } catch (RuntimeException schedulingFailure) {
                warningLogger.accept("Could not schedule readiness task for " + operationName + ": "
                        + rootMessage(schedulingFailure));
            }
        });
    }

    public static String rootMessage(Throwable throwable) {
        Throwable current = Objects.requireNonNull(throwable, "throwable");
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
    }
}
