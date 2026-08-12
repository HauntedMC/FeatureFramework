# 07 — Advanced lifecycle and reload choices

Use this after you are comfortable with normal feature ownership.

## Soft reload or recreate?

A feature's `applyConfiguration()` communicates whether it can safely absorb a configuration change. The managed default is `RECREATE_REQUIRED`.

Prefer recreation when callbacks, dependencies, connections, command topology, or state machines would otherwise retain old state. A clean recreation is usually cheaper than debugging a partially updated live feature.

## Async work on Paper

Use the feature task manager for owned async work (`runAsync`, `supplyAsync`, async scheduling). The future/task remains associated with feature lifecycle cleanup. Never call unsafe Bukkit APIs from an async continuation merely because the task manager created it.

## Direct platform resources

If FeatureFramework has no adapter for a third-party registration, you may register it directly, but then the feature must explicitly release it in `disable()` (or another lifecycle hook appropriate to that integration).

## Service shutdown

Callable feature services are withdrawn before `disable()` releases domain state. Design service consumers around feature/capability lifetime rather than caching implementation objects forever.

## Production test

Exercise `enable -> use -> reload/disable -> verify cleanup -> enable -> use` repeatedly. One successful first boot does not prove lifecycle correctness.
