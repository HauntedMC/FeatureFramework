# 09 — Paper with DataRegistry

This self-contained example shows how a normal Paper feature consumes the typed DataRegistry resource extension.

The bootstrap attaches `PaperDataRegistryContributor`, using `PaperDataRegistryPluginDiscovery` to resolve the plugin.

## Files

- `MyPlugin.java` — builds a normal custom Paper host without DataProvider, then enables DataRegistry plugin discovery.
- `IdentityFeature.java` — implements `PaperDataRegistryAccess`, registers a join listener, and waits for a player's DataRegistry identity before using it.

## Why use `PaperDataRegistryAccess`?

It provides the DataRegistry-aware feature context needed by `PaperDataRegistryIdentityGate`:

```java
dataRegistry();
PaperDataRegistryIdentityGate.runWhenReady(this, player, ...);
```

The readiness gate waits asynchronously for DataRegistry, then schedules the continuation through the feature's Paper task manager. It also checks that the plugin/player are still valid before invoking your action.

That matters on Paper: waiting for persistence must not block the primary thread, while the eventual continuation may need to return to the feature's normal Paper lifecycle.

## Plugin discovery

`PaperDataRegistryPluginDiscovery.supplier(...)` finds the Bukkit plugin and requires its instance to implement `DataRegistryApiProvider`. If the application already owns a registry, pass its supplier directly to the contributor.

The feature also declares `requiresPlugins = "DataRegistry"`, so its external dependency is visible beside the implementation rather than hidden in `initialize()`.
