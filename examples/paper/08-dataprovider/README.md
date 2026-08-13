# 08 — Paper with DataProvider

This example is self-contained and shows the first case where the ready-to-use `PaperFeatureHost` is intentionally not enough.

`PaperFeatureHost` uses `Void` as its data-manager type. To give each feature a lifecycle-owned `FeatureDataManager`, build the same framework pieces explicitly with `PaperFeatureHostComposition` and `PaperFeatureResourcesFactory.withDataProvider(...)`.

## Files

- `MyPlugin.java` — creates the runtime, config/localization services, Paper resource factory, and custom host composition.
- `PlayerStorageFeature.java` — accesses its own `FeatureDataManager` and registers a DataProvider database connection.

## What happens at startup

```text
MyPlugin
  -> FeatureRuntime
  -> ConfigService / DefaultFeatureConfiguration
  -> PaperLocalization
  -> PaperFeatureResourcesFactory.withDataProvider(...)
       -> creates one FeatureDataManager per feature
       -> binds it to that feature name
  -> PaperFeatureHostComposition
       -> creates PlayerStorageFeature
            -> resources().getDataManager()
            -> registerConnection(...)
```

The DataProvider API itself is not created by FeatureFramework. On Paper, the resource factory resolves `DataProviderAPI` through Bukkit's `ServicesManager`. The DataProvider plugin must therefore already be installed and exposing its API service.

## Why the manager is feature-owned

`FeatureDataManager` creates a DataProvider scope for the current feature and tracks its connections, data-access objects, and ORM contexts. When the feature stops, FeatureFramework quiesces the manager and closes its data resources with the rest of the feature lifecycle.

You should not manually close the connection registered in `PlayerStorageFeature.disable()`; the resource scope owns it.

## Connection names

The sample uses `FeatureDataManager.PLAYER_DATA_RW_CONNECTION` as the configured DataProvider connection name. Your DataProvider installation must define that connection, or you should replace it with one that exists in your environment.

`registerConnection(...)` returns an `Optional<DatabaseProvider>`. A real feature should fail clearly or disable the affected functionality if a required connection cannot be created.

## ORM and Redis

The same manager also exposes helpers such as `createPlayerOrmContext(...)`, `createSystemOrmContext(...)`, `registerRedisMessagingProvider(...)`, and typed `registerDataAccess(...)`. Keep those resources on the feature's manager so their lifetime remains aligned with the feature.
