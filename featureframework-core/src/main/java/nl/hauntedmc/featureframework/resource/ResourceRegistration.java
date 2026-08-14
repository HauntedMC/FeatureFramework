package nl.hauntedmc.featureframework.resource;

/** A feature-owned resource and its independently closeable registration. */
public interface ResourceRegistration<T> extends AutoCloseable {
    T value();

    @Override
    void close();
}
