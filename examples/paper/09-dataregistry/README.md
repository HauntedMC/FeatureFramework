# 09 — Paper with DataRegistry

This self-contained example shows how a feature consumes DataRegistry through FeatureFramework's typed `PaperDataRegistryFeature` base.

The ready-to-use `PaperFeatureHost` does not configure a DataRegistry supplier, so this example uses `PaperFeatureHostComposition` and calls `.dataRegistryPlugin("DataRegistry")`.

## Files

- `MyPlugin.java` — builds a normal custom Paper host without DataProvider, then enables DataRegistry plugin discovery.
- `IdentityFeature.java` — extends `PaperDataRegistryFeature`, registers a join listener, and waits for a player's DataRegistry identity before using it.

## Why use `PaperDataRegistryFeature`?

It provides the DataRegistry-aware feature context needed by `PaperDataRegistryIdentityGate`:

```java
dataRegistry();
PaperDataRegistryIdentityGate.runWhenReady(this, player, ...);
```

The readiness gate waits asynchronously for DataRegistry, then schedules the continuation through the feature's Paper task manager. It also checks that the plugin/player are still valid before invoking your action.

That matters on Paper: waiting for persistence must not block the primary thread, while the eventual continuation may need to return to the feature's normal Paper lifecycle.

## Plugin discovery

`.dataRegistryPlugin("DataRegistry")` asks FeatureFramework to find that Bukkit plugin and requires its instance to implement `DataRegistryApiProvider`. If your application already owns a `DataRegistryApi`, use `.dataRegistry(() -> yourRegistry)` instead.

The feature also declares `.requiresPlugins("DataRegistry")`, so its external dependency is visible in the feature definition rather than hidden in `initialize()`.
