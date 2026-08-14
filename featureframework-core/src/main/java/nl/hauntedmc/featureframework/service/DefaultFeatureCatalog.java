package nl.hauntedmc.featureframework.service;

import nl.hauntedmc.featureframework.api.feature.*;

import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** Thread-safe public projection of the runtime feature lifecycle. */
public final class DefaultFeatureCatalog implements FeatureCatalog {

    private static final int MAX_FAILURE_MESSAGE_LENGTH = 160;

    private record Entry(FeatureMetadata metadata, boolean configuredEnabled, FeatureState state,
                         Optional<String> failure, Optional<FeatureFailure> failureDetail,
                         Set<FeatureId> unavailableDependencies, Instant lastTransitionAt,
                         Optional<Instant> lastSuccessfulActivationAt, long generation) {
    }

    private final ConcurrentHashMap<FeatureId, Entry> entries = new ConcurrentHashMap<>();
    private final Clock clock;
    private final CopyOnWriteArrayList<FeatureCatalogListener> listeners = new CopyOnWriteArrayList<>();

    public DefaultFeatureCatalog() {
        this(Clock.systemUTC());
    }

    DefaultFeatureCatalog(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public void register(FeatureMetadata metadata) {
        Objects.requireNonNull(metadata, "metadata");
        Instant now = clock.instant();
        Entry entry = new Entry(metadata, false, FeatureState.DISABLED, Optional.empty(),
                Optional.empty(), Set.of(), now, Optional.empty(), 0L);
        entries.put(metadata.id(), entry);
        notifyChanged(entry);
    }

    public void setConfiguredEnabled(FeatureId id, boolean enabled) {
        Entry[] previous = new Entry[1];
        Entry next = entries.compute(requireKnown(id), (ignored, entry) -> {
            previous[0] = requireEntry(id, entry);
            if (entry.configuredEnabled() == enabled) {
                return entry;
            }
            return new Entry(entry.metadata(), enabled, entry.state(), entry.failure(), entry.failureDetail(),
                    entry.unavailableDependencies(), entry.lastTransitionAt(), entry.lastSuccessfulActivationAt(),
                    entry.generation() + 1);
        });
        if (next != previous[0]) {
            notifyChanged(next);
        }
    }

    /** Updates unavailable feature prerequisites without conflating them with disabled configuration. */
    public void setUnavailableDependencies(FeatureId id, Set<FeatureId> unavailableDependencies) {
        Set<FeatureId> normalized = unavailableDependencies == null ? Set.of() : Set.copyOf(unavailableDependencies);
        Entry[] previous = new Entry[1];
        Entry next = entries.compute(requireKnown(id), (ignored, entry) -> {
            previous[0] = requireEntry(id, entry);
            if (entry.unavailableDependencies().equals(normalized)) {
                return entry;
            }
            return new Entry(entry.metadata(), entry.configuredEnabled(), entry.state(), entry.failure(),
                    entry.failureDetail(), normalized, entry.lastTransitionAt(), entry.lastSuccessfulActivationAt(),
                    entry.generation() + 1);
        });
        if (next != previous[0]) {
            notifyChanged(next);
        }
    }

    public void transition(FeatureId id, FeatureState state) {
        transition(id, state, Optional.empty());
    }

    public void fail(FeatureId id, Throwable failure) {
        fail(id, "lifecycle", failure);
    }

    /** Records a stable lifecycle phase so API consumers can distinguish startup from cleanup failures. */
    public void fail(FeatureId id, String phase, Throwable failure) {
        Objects.requireNonNull(phase, "phase");
        Objects.requireNonNull(failure, "failure");
        String message = failure.getMessage();
        String safe = message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
        safe = safe.length() > MAX_FAILURE_MESSAGE_LENGTH
                ? safe.substring(0, MAX_FAILURE_MESSAGE_LENGTH)
                : safe;
        transition(id, FeatureState.FAILED, Optional.of(safe),
                Optional.of(new FeatureFailure(normalizePhase(phase), failure.getClass().getSimpleName(), Optional.of(safe))));
    }

    @Override
    public Optional<FeatureSnapshot> find(FeatureId id) {
        Entry entry = entries.get(Objects.requireNonNull(id, "id"));
        return entry == null ? Optional.empty() : Optional.of(snapshot(entry, clock.instant()));
    }

    @Override
    public List<FeatureSnapshot> snapshot() {
        Instant observedAt = clock.instant();
        return entries.values().stream()
                .map(entry -> snapshot(entry, observedAt))
                .sorted(Comparator.comparing(value -> value.metadata().id()))
                .toList();
    }

    @Override
    public AutoCloseable subscribe(FeatureCatalogListener listener) {
        Objects.requireNonNull(listener, "listener");
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    private void transition(FeatureId id, FeatureState state, Optional<String> failure) {
        transition(id, state, failure, Optional.empty());
    }

    private void transition(FeatureId id, FeatureState state, Optional<String> failure,
                            Optional<FeatureFailure> failureDetail) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(state, "state");
        Entry next = entries.compute(id, (ignored, current) -> {
            if (current == null) {
                throw new IllegalArgumentException("Unknown feature: " + id);
            }
            if (!isAllowed(current.state(), state)) {
                throw new IllegalStateException("Invalid feature state transition: " + current.state() + " -> " + state);
            }
            Instant now = clock.instant();
            Optional<Instant> activated = state == FeatureState.ACTIVE ? Optional.of(now) : current.lastSuccessfulActivationAt();
            return new Entry(current.metadata(), current.configuredEnabled(), state, failure, failureDetail,
                    current.unavailableDependencies(), now, activated, current.generation() + 1);
        });
        notifyChanged(next);
    }

    private static FeatureSnapshot snapshot(Entry entry, Instant observedAt) {
        return new FeatureSnapshot(entry.metadata(), entry.configuredEnabled(), entry.state(), entry.failure(),
                entry.failureDetail(), entry.unavailableDependencies(), entry.lastTransitionAt(),
                entry.lastSuccessfulActivationAt(), entry.generation(), observedAt);
    }

    private void notifyChanged(Entry entry) {
        FeatureSnapshot snapshot = snapshot(entry, clock.instant());
        listeners.forEach(listener -> {
            try {
                listener.stateChanged(snapshot);
            } catch (RuntimeException ignored) {
                // Listener isolation is required for lifecycle progress.
            }
        });
    }

    private static String normalizePhase(String phase) {
        String normalized = phase.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("phase must not be blank");
        }
        return normalized;
    }

    private static FeatureId requireKnown(FeatureId id) { return Objects.requireNonNull(id, "id"); }

    private static Entry requireEntry(FeatureId id, Entry entry) {
        if (entry == null) {
            throw new IllegalArgumentException("Unknown feature: " + id);
        }
        return entry;
    }

    private static boolean isAllowed(FeatureState from, FeatureState to) {
        if (from == to) return true;
        return switch (from) {
            case DISABLED -> to == FeatureState.STARTING || to == FeatureState.FAILED;
            case STARTING -> to == FeatureState.ACTIVE || to == FeatureState.FAILED || to == FeatureState.STOPPING;
            case ACTIVE -> to == FeatureState.STOPPING || to == FeatureState.FAILED;
            case STOPPING -> to == FeatureState.DISABLED || to == FeatureState.FAILED;
            case FAILED -> to == FeatureState.STARTING || to == FeatureState.DISABLED;
        };
    }
}
