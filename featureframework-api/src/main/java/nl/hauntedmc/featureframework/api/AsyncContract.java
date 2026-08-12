package nl.hauntedmc.featureframework.api;

/**
 * Shared contract for public API methods. Synchronous queries are thread-safe and must not do
 * blocking I/O. Completion stages may finish on a provider-managed worker or a Paper thread, so
 * callbacks must be non-blocking and integrations must choose their own executor for blocking work.
 * Cancellation is best-effort and does not cancel already-submitted persistence work. Providers
 * complete outstanding operations exceptionally with a typed API failure when they unload; callers
 * are responsible for their own timeouts.
 */
public final class AsyncContract {
    private AsyncContract() { }
}
