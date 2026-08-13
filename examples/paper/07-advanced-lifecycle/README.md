# 07 — Advanced lifecycle and reloads

Use this after the normal ownership model is clear.

## Soft reload or recreation?

The managed default is `RECREATE_REQUIRED`. Prefer recreation when configuration affects callbacks, dependencies, clients, command structure, or long-lived state. Use a soft update only when the changed values can be replaced safely while the feature stays live.

## Async work

Use the feature task manager for owned async/repeating work. An async task is still subject to normal Bukkit thread-safety rules; switch back to a synchronous Paper task before touching APIs that require the primary thread.

## Resources FeatureFramework does not own

If you register a third-party callback or create a client directly, keep its cleanup handle and release it during feature shutdown.

## Services

Feature-owned services are withdrawn before `disable()` releases provider state. Consumers should resolve services according to feature lifetime rather than caching implementation objects forever.

## Test the second lifecycle

A useful production test is:

```text
enable -> use -> reload/disable -> verify cleanup -> enable -> use
```

Run it more than once. Leaks often show up on the second cycle.
