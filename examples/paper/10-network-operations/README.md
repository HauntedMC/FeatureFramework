# 10 — Paper network operations

This design example shows the missing layer between “a plugin starts” and “a network can operate that plugin while it is live.” The `FeatureAdmin` feature owns a permissioned `/features` Brigadier command. The command is only an adapter; it reads and mutates the real host graph.

The included `NetworkStatusFeature` is a small managed target for the command. Its configurable heartbeat task returns `RECREATE_REQUIRED`, so `/features softreload NetworkStatus` demonstrates the safe path when configuration changes the live resource shape.

```text
/features list | info <feature>
  -> FeatureCommandModel reads the authoritative feature catalog

/features enable | disable | softreload | reload <feature>
  -> PaperFeatureHost returns a structured operation response
  -> FeatureOperationMessages maps it to localization keys/placeholders

/features reload-all
  -> host.reload() reloads host configuration/localization
  -> reconciles disabled, live, and newly enabled features in graph order
```

## Why this matters

The operational boundary is the same lifecycle boundary used at startup. Disabling a queue integration removes the commands, listeners, tasks, capabilities, and DataProvider resources registered by that feature generation; recreating it builds a clean generation instead of layering another registration on top of the old one.

Use a separate permission for every mutation, log actor/request/result/affected dependents, and protect the `FeatureAdmin` feature from disabling itself if it is the only recovery path. A graph-wide reload is an elevated operation, not a replacement for a normal `/reload` workflow.

## Local files versus feature lifecycle

Expose a narrow `reloadlocal <feature>` only for files that are safe to reread without changing runtime state, commonly feature localization:

```java
PaperFeature<MyPlugin> feature = plugin.featureHost()
        .findLoaded(FeatureId.of(featureKey)).orElse(null);
feature.context().localization().reloadLocalization();
```

If the changed value affects a scheduled task, listener policy, remote client, data subscription, cache, or capability, call `host.softReload(id)` or `host.recreate(id)` instead. A feature can return `RECREATE_REQUIRED` from `applyConfiguration()` to make the safe choice explicit.

## A realistic graph to combine with examples 08 and 09

```text
IdentityFeature (DataRegistry)
  provides PlayerIdentityApi
       └──> SessionPolicyFeature

NetworkTransportFeature (DataProvider)
  owns Redis/database resources
  provides NetworkEventsApi
       ├──> CapacityFeature
       └──> OperationsAuditFeature

FeatureAdmin
  manages and observes the full graph
```

`NetworkTransportFeature` owns the raw DataProvider managers and exposes a behavior-focused capability. Consumers do not retain a database or messaging handle across a provider recreation. This keeps the complicated part of a feature—repository, publisher, subscription, policies, listeners, and commands—as normal testable Java within one meaningful lifetime.

For a concrete command skeleton and full operational guidance, read [Operating a large feature plugin](../../../docs/guides/OPERATING-A-LARGE-FEATURE-PLUGIN.md). Pair this example with [08-dataprovider](../08-dataprovider/README.md) and [09-dataregistry](../09-dataregistry/README.md).
