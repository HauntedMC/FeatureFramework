package nl.hauntedmc.featureframework.resource;

import nl.hauntedmc.featureframework.lifecycle.FeatureResourceState;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/** Owns arbitrary resources for exactly one feature generation. */
public final class FeatureResourceOwner {
    private final List<Entry<?>> entries = new ArrayList<>();
    private FeatureResourceState state = FeatureResourceState.OPEN;

    public <T extends AutoCloseable> ResourceRegistration<T> own(T value) {
        return own(value, AutoCloseable::close);
    }

    public <T> ResourceRegistration<T> own(T value, ThrowingConsumer<? super T> cleanup) {
        return ownPhased(value, ignored -> { }, cleanup);
    }

    public <T> ResourceRegistration<T> ownIngress(T value, ThrowingConsumer<? super T> quiesce) {
        return ownPhased(value, quiesce, ignored -> { });
    }

    public <T> ResourceRegistration<T> ownIngress(
            T value,
            Function<? super T, ? extends CompletionStage<?>> quiesce,
            Duration timeout
    ) {
        Objects.requireNonNull(quiesce, "quiesce");
        Duration requiredTimeout = positive(timeout);
        return ownIngress(value, resource -> await(quiesce.apply(resource), requiredTimeout));
    }

    public synchronized <T> ResourceRegistration<T> ownPhased(
            T value,
            ThrowingConsumer<? super T> quiesce,
            ThrowingConsumer<? super T> cleanup
    ) {
        T requiredValue = Objects.requireNonNull(value, "value");
        Entry<T> entry = new Entry<>(requiredValue,
                Objects.requireNonNull(quiesce, "quiesce"), Objects.requireNonNull(cleanup, "cleanup"));
        if (state != FeatureResourceState.OPEN) {
            Throwable failure = null;
            try { entry.quiesce(); } catch (Throwable current) { failure = current; }
            try { entry.cleanup(); } catch (Throwable current) { failure = append(failure, current); }
            if (failure != null) {
                IllegalStateException rejected = new IllegalStateException(
                        "Feature resources are no longer accepting registrations");
                rejected.addSuppressed(failure);
                throw rejected;
            }
            throw new IllegalStateException("Feature resources are no longer accepting registrations");
        }
        entries.add(entry);
        return new Registration<>(this, entry);
    }

    public synchronized FeatureResourceState state() { return state; }

    public synchronized void quiesce() {
        if (state != FeatureResourceState.OPEN) return;
        state = FeatureResourceState.QUIESCING;
        throwIfPresent(runReverse(true));
    }

    public synchronized void cleanup() {
        if (state == FeatureResourceState.CLOSED) return;
        Throwable failure = null;
        if (state == FeatureResourceState.OPEN) {
            state = FeatureResourceState.QUIESCING;
            failure = runReverse(true);
        }
        failure = append(failure, runReverse(false));
        entries.clear();
        state = FeatureResourceState.CLOSED;
        throwIfPresent(failure);
    }

    private Throwable runReverse(boolean quiesce) {
        Throwable failure = null;
        for (int index = entries.size() - 1; index >= 0; index--) {
            try {
                if (quiesce) entries.get(index).quiesce();
                else entries.get(index).cleanup();
            } catch (Throwable current) {
                failure = append(failure, current);
            }
        }
        return failure;
    }

    private synchronized void close(Entry<?> entry) {
        if (!entries.remove(entry)) return;
        Throwable failure = null;
        try { entry.quiesce(); } catch (Throwable current) { failure = current; }
        try { entry.cleanup(); } catch (Throwable current) { failure = append(failure, current); }
        throwIfPresent(failure);
    }

    private static Duration positive(Duration duration) {
        Duration required = Objects.requireNonNull(duration, "timeout");
        if (required.isZero() || required.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        return required;
    }

    private static void await(CompletionStage<?> stage, Duration timeout) throws Exception {
        Objects.requireNonNull(stage, "cleanup stage").toCompletableFuture()
                .get(timeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    private static Throwable append(Throwable first, Throwable next) {
        if (first == null) return next;
        if (next != null) first.addSuppressed(next);
        return first;
    }

    private static void throwIfPresent(Throwable failure) {
        if (failure == null) return;
        if (failure instanceof RuntimeException runtime) throw runtime;
        if (failure instanceof Error error) throw error;
        throw new IllegalStateException("Feature resource cleanup failed", failure);
    }

    private static final class Entry<T> {
        private final T value;
        private final ThrowingConsumer<? super T> quiesce;
        private final ThrowingConsumer<? super T> cleanup;
        private final AtomicBoolean quiesced = new AtomicBoolean();
        private final AtomicBoolean cleaned = new AtomicBoolean();

        private Entry(T value, ThrowingConsumer<? super T> quiesce, ThrowingConsumer<? super T> cleanup) {
            this.value = value;
            this.quiesce = quiesce;
            this.cleanup = cleanup;
        }

        private void quiesce() throws Exception {
            if (quiesced.compareAndSet(false, true)) quiesce.accept(value);
        }

        private void cleanup() throws Exception {
            if (cleaned.compareAndSet(false, true)) cleanup.accept(value);
        }
    }

    private static final class Registration<T> implements ResourceRegistration<T> {
        private final FeatureResourceOwner owner;
        private final Entry<T> entry;
        private final AtomicBoolean closed = new AtomicBoolean();

        private Registration(FeatureResourceOwner owner, Entry<T> entry) {
            this.owner = owner;
            this.entry = entry;
        }

        @Override public T value() { return entry.value; }
        @Override public void close() { if (closed.compareAndSet(false, true)) owner.close(entry); }
    }
}
