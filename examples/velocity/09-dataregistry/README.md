# 09 — Velocity with DataRegistry

This self-contained example uses FeatureFramework's `VelocityDataRegistryFeature` base and DataRegistry readiness gate.

`VelocityFeatureHost` does not configure DataRegistry by default, so the bootstrap uses `VelocityFeatureHostComposition` and `.dataRegistryPlugin("dataregistry")`.

## Files

- `ProxyPlugin.java` — creates the custom host and enables DataRegistry plugin discovery.
- `IdentityFeature.java` — listens for logins and waits until DataRegistry reports the player's identity as ready.

## DataRegistry discovery

On Velocity, `.dataRegistryPlugin("dataregistry")` resolves the plugin container by id. Its instance must implement `DataRegistryApiProvider`.

If you already have a `DataRegistryApi` reference, configure `.dataRegistry(() -> registry)` instead.

## Readiness

DataRegistry-backed identity can be asynchronous. `VelocityDataRegistryIdentityGate.runWhenReady(...)` waits without blocking and schedules the continuation through the feature's owned task manager. Before running the action, the gate resolves the connected player again by UUID.

`VelocityDataRegistryFeature` also exposes `playerReferences()`, a shared `PlayerReferenceResolver` for immutable player-reference lookups during the current feature generation.
