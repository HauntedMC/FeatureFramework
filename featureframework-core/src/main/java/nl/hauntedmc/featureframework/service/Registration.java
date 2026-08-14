package nl.hauntedmc.featureframework.service;

/** Idempotent handle for a framework-owned registration. */
@FunctionalInterface
public interface Registration extends AutoCloseable {
    @Override
    void close();
}
