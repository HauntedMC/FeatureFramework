package nl.hauntedmc.featureframework.service;

/** Lifecycle callbacks for internal feature-service provider generations. */
public interface InternalServiceListener {
    default void available(Class<?> type, long generation) { }
    default void unavailable(Class<?> type, long generation) { }
    default void replaced(Class<?> type, long previousGeneration, long nextGeneration) { }
}
