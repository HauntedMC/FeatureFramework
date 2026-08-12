package nl.hauntedmc.featureframework.persistence;

import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.*;

/** Guards synchronous persistence fallbacks from event-loop and main-thread blocking. */
public final class BoundedPersistenceLookup {
    private BoundedPersistenceLookup() { }

    public static boolean mayWaitOnCurrentThread() {
        return !isLikelyEventThread(Thread.currentThread().getName());
    }

    public static boolean isLikelyEventThread(String threadName) {
        if (threadName == null) {
            return false;
        }
        String normalized = threadName.toLowerCase(Locale.ROOT);
        return normalized.contains("server thread")
                || normalized.contains("main")
                || normalized.contains("event")
                || normalized.contains("netty");
    }

    public static <T> Optional<T> awaitOptional(
            CompletionStage<Optional<T>> stage,
            long timeout,
            TimeUnit unit
    ) {
        if (stage == null) {
            return Optional.empty();
        }
        CompletableFuture<Optional<T>> future = stage.toCompletableFuture();
        try {
            Optional<T> result = future.get(timeout, unit);
            return result == null ? Optional.empty() : result;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (TimeoutException exception) {
            future.cancel(true);
            return Optional.empty();
        } catch (ExecutionException | RuntimeException exception) {
            return Optional.empty();
        }
    }
}
