# 09 — Velocity with DataRegistry

This self-contained example uses `VelocityDataRegistryAccess` and the DataRegistry readiness gate.

The bootstrap attaches `VelocityDataRegistryContributor`, using plugin discovery for `dataregistry`.

## Files

- `ProxyPlugin.java` — creates the custom host and enables DataRegistry plugin discovery.
- `IdentityFeature.java` — listens for logins and waits until DataRegistry reports the player's identity as ready.

## DataRegistry discovery

On Velocity, `VelocityDataRegistryPluginDiscovery` resolves the plugin container by id. Its instance must implement `DataRegistryApiProvider`.

If you already have a `DataRegistryApi` reference, pass its supplier directly to the contributor.

## Readiness

DataRegistry-backed identity can be asynchronous. `VelocityDataRegistryIdentityGate.runWhenReady(...)` waits without blocking and schedules the continuation through the feature's owned task manager.

The gate is connection-fenced: it captures the exact originating Velocity `Player` object before starting the asynchronous wait and only runs the continuation if that same connection is still current. If DataRegistry already exposes a live network session, the gate also captures its `SessionFence` and requires the same authoritative session immediately before execution. A stale callback from a disconnected connection therefore cannot run against a rapid reconnect that happens to use the same player UUID.

If no DataRegistry session has been published yet, the exact Velocity connection identity remains the fallback fence so activation-time and early lifecycle use stays non-blocking and compatible.

`VelocityDataRegistryAccess` also exposes `playerReferences()`, a shared `PlayerReferenceResolver` for immutable player-reference lookups during the current feature generation.
