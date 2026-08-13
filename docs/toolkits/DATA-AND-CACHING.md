# Data and Caching

Data clients and caches should have a clear owner just like listeners and tasks.

## Data resources

The ready-to-use Paper and Velocity hosts use a no-data-provider composition. Applications that want framework-managed data resources can use the platform host-composition layer and provide their own data manager/provider.

If several features need the same backend, prefer exposing the behavior they need through a capability instead of handing every feature a raw client:

```text
RedisFeature
  owns Redis client
  provides NetworkBusApi
       ├──> QueueFeature
       └──> ModerationFeature
```

This keeps Redis-specific code in one place and lets consumers depend on the contract instead of the library/client.

## Caches

Both platform resource scopes include a `FeatureCacheManager`. Caches registered there are cleaned up with the feature.

Before adding a cache, be clear about its key, invalidation rule, lifetime, and whether stale values are acceptable. Do not use a cache as hidden cross-feature state.

## Persistence code

Repositories and data services can remain normal classes inside a feature:

```text
ProfilesFeature
├── ProfileRepository
├── ProfileService
└── ProfileCache
```

If another feature needs profile behavior, expose a `PlayerProfileApi` capability instead of the repository or database handle.
