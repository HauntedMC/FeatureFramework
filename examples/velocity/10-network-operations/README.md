# 10 — Velocity network operations

This design example applies the same control-plane pattern to a proxy: a `FeatureAdmin` feature owns an administrative `/features` command, while `VelocityFeatureHost` remains the authority for graph operations.

The included `NetworkStatusFeature` is a real managed target: it owns a configurable heartbeat task and requests recreation when that task's interval changes. This makes `softreload` meaningful without pretending a small example contains a network backend.

```text
list/info           -> FeatureCommandModel reads the host registry
enable/disable      -> structured lifecycle responses and persisted state
softreload/reload   -> safe live config application or a fresh feature generation
reload-all          -> reload host config/localization and reconcile the configured graph
```

The command should use `FeatureOperationMessages` to turn operation responses into your localized messages, and `FeatureCommandModel` for completions and status data. Keep command parsing, permissions, confirmation, and audit trails outside the feature host; keep every actual state transition inside it.

## Why a proxy benefits especially

A proxy often combines network messaging, backend-directory state, player identity, queue/admission policy, and cross-server moderation. A failed or changed integration must not leave an old listener, task, command, Redis subscription, or capability alive after its replacement.

```text
NetworkBusFeature (DataProvider)
  owns Redis messaging and data access
  provides NetworkEventsApi
       ├──> CapacityFeature
       ├──> QueueFeature
       └──> OperationsAuditFeature

IdentityFeature (DataRegistry)
  provides PlayerIdentityApi
       └──> ConnectionPolicyFeature
```

Model those APIs as capabilities; do not hand the raw messaging or database client to every consumer. Reloading the provider can then safely recreate consumers that need it.

## Local messages are not a graph reload

A `reloadlocal <feature>` command may deliberately reload only the feature's localization scope:

```java
VelocityFeature<Object, Void> feature = plugin.featureHost()
        .managedHost().registry().getLoadedFeature(featureKey);
feature.getContext().localization().reloadLocalization();
```

Do not present this as a full config reload. If a setting governs a task, data subscription, client, cache, capability, or concurrent policy, use `softReloadFeature` or `reloadFeature`; use `host.reload()` to reconcile the complete edited graph.

Velocity lifecycle operations execute on the caller, so ensure the command path and any asynchronous administration work are coordinated for your plugin's concurrency model. The framework still owns cleanup, but it does not create a Bukkit-style main thread.

For the complete control-plane design, see [Operating a large feature plugin](../../../docs/guides/OPERATING-A-LARGE-FEATURE-PLUGIN.md), then pair this reference with [08-dataprovider](../08-dataprovider/README.md) and [09-dataregistry](../09-dataregistry/README.md).
