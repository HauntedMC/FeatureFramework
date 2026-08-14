package nl.hauntedmc.featureframework.lifecycle;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/** Shared state and ownership tracker for feature-scoped platform registrations. */
public final class FeatureRegistrationTracker<T> {
    private final List<T> registrations = new ArrayList<>();
    private final boolean uniqueTracking;
    private FeatureResourceState state = FeatureResourceState.OPEN;

    public FeatureRegistrationTracker(boolean uniqueTracking) {
        this.uniqueTracking = uniqueTracking;
    }

    /**
     * Performs the native registration and then tracks its owner for cleanup.
     *
     * <p>Native registration is deliberately always invoked. Some platforms allow one listener object
     * to register multiple event handlers while cleanup only needs to track that listener once.</p>
     */
    public synchronized void register(T registration, Consumer<T> registrar) {
        requireOpen();
        T required = Objects.requireNonNull(registration, "registration");
        Objects.requireNonNull(registrar, "registrar").accept(required);
        if (!uniqueTracking || !registrations.contains(required)) registrations.add(required);
    }

    public synchronized void unregister(T registration, Consumer<T> unregistrar) {
        if (registration == null) return;
        Objects.requireNonNull(unregistrar, "unregistrar").accept(registration);
        registrations.remove(registration);
    }

    public synchronized void quiesce() {
        if (state == FeatureResourceState.OPEN) state = FeatureResourceState.QUIESCING;
    }

    public synchronized void unregisterAll(Consumer<T> unregistrar) {
        quiesce();
        Throwable failure = null;
        Consumer<T> requiredUnregistrar = Objects.requireNonNull(unregistrar, "unregistrar");
        for (T registration : List.copyOf(registrations)) {
            try {
                requiredUnregistrar.accept(registration);
                registrations.remove(registration);
            } catch (Throwable stepFailure) {
                if (failure == null) failure = stepFailure;
                else failure.addSuppressed(stepFailure);
            }
        }
        if (failure == null && registrations.isEmpty()) state = FeatureResourceState.CLOSED;
        if (failure != null) throwUnchecked(failure);
    }

    public synchronized int size() { return registrations.size(); }
    public synchronized FeatureResourceState state() { return state; }

    private void requireOpen() {
        if (state != FeatureResourceState.OPEN) {
            throw new IllegalStateException("Registration tracker is " + state);
        }
    }

    @SuppressWarnings("unchecked")
    private static <E extends Throwable> void throwUnchecked(Throwable failure) throws E { throw (E) failure; }
}
