package nl.hauntedmc.featureframework.resource;

/** Consumer whose cleanup operation may fail. */
@FunctionalInterface
public interface ThrowingConsumer<T> {
    void accept(T value) throws Exception;
}
