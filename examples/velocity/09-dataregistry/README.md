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

DataRegistry-backed identity can be asynchronous. `VelocityDataRegistryIdentityGate.runWhenReady(...)` waits without blocking and schedules the continuation through the feature's owned task manager. Before running the action, the gate resolves the connected player again by UUID.

`VelocityDataRegistryAccess` also exposes `playerReferences()`, a shared `PlayerReferenceResolver` for immutable player-reference lookups during the current feature generation.
