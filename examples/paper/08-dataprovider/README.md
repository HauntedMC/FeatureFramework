# 08 — Paper with DataProvider

This example is self-contained and shows how the normal `PaperFeatureHost` gains lifecycle-owned
DataProvider resources through `PaperDataProviderContributor`.

## Files

- `MyPlugin.java` — configures the host façade and attaches the DataProvider contributor.
- `PlayerStorageFeature.java` — accesses its own `DataProviderResources` and registers a DataProvider database connection.

## What happens at startup

```text
MyPlugin
  -> PaperFeatureHost
  -> PaperDataProviderContributor
       -> creates one DataProviderResources per feature
       -> binds it to that feature name
       -> creates PlayerStorageFeature
            -> resources().extensions().require(DataProviderResources.KEY)
            -> registerConnection(...)
```

The DataProvider API itself is not created by FeatureFramework. `PaperDataProviderApiResolver` resolves
it through Bukkit's `ServicesManager`; the DataProvider plugin must expose that API service.

## Why the manager is feature-owned

`DataProviderResources` creates a DataProvider scope for the current feature and tracks its connections, data-access objects, and ORM contexts. When the feature stops, FeatureFramework quiesces the manager and closes its data resources with the rest of the feature lifecycle.

You should not manually close the connection registered in `PlayerStorageFeature.disable()`; the resource scope owns it.

## Connection names

The sample uses `DataProviderConnections.PLAYER_DATA_RW` as the configured DataProvider connection name. Your DataProvider installation must define that connection, or you should replace it with one that exists in your environment.

`registerConnection(...)` returns an `Optional<DatabaseProvider>`. A real feature should fail clearly or disable the affected functionality if a required connection cannot be created.

## ORM and Redis

The same manager also exposes helpers such as `createPlayerOrmContext(...)`, `createSystemOrmContext(...)`, `registerRedisMessagingProvider(...)`, and typed `registerDataAccess(...)`. Keep those resources on the feature's manager so their lifetime remains aligned with the feature.
