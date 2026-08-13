# 08 — Velocity with DataProvider

This self-contained example gives each managed Velocity feature its own lifecycle-owned `FeatureDataManager`.

The normal `VelocityFeatureHost` intentionally uses no data manager, so DataProvider integration uses `VelocityFeatureHostComposition` plus `VelocityFeatureResourcesFactory.withDataProvider(...)`.

## Files

- `ProxyPlugin.java` — builds the custom runtime/config/localization/resource composition.
- `NetworkStorageFeature.java` — obtains its `FeatureDataManager` and registers a Redis messaging provider.

## DataProvider discovery

`VelocityFeatureResourcesFactory.withDataProvider(...)` resolves the plugin with id `dataprovider`. Its plugin instance must implement `DataProviderApiSupplier`.

Each feature receives a separate `FeatureDataManager` bound to its feature name. The manager lazily creates its DataProvider scope and owns registered database/messaging/ORM resources until the feature stops.

## Why Redis in this example?

Proxy features often need network messaging, so the sample uses:

```java
registerRedisMessagingProvider("network", "hauntedmc")
```

Replace `hauntedmc` with a Redis messaging connection configured in your DataProvider setup.

The same manager can register relational database connections, typed data access, and ORM contexts. The ownership rule does not change: create them through the feature's manager and let FeatureFramework close them with the feature.
