package nl.hauntedmc.featureframework.api.service;

/** Lifecycle callbacks invoked after a capability registry change. Callbacks must be non-blocking. */
public interface CapabilityListener {
    default void available(Class<?> type, long generation) { }
    default void unavailable(Class<?> type, long generation) { }
    default void replaced(Class<?> type, long previousGeneration, long nextGeneration) { }
}
