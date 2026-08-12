# Data and Caching

FeatureFramework treats data and caches as lifecycle-owned resources rather than invisible global state.

## Data resources

Platform feature resources can be composed with a data manager/provider. The ready-to-use hosts default to a no-data-provider composition; applications that need custom data resources can use the platform host-composition layer.

Use a feature-scoped data manager when the data resource's lifetime should follow one feature. Use a shared infrastructure capability when several features need the same logical backend without sharing its implementation directly.

Example:

```text
RedisFeature
  owns Redis client
  provides NetworkBusApi

QueueFeature ------> NetworkBusApi
ModerationFeature -> NetworkBusApi
```

That is preferable to handing the raw Redis client to every feature.

## Caches

Both platform resource scopes include a `FeatureCacheManager`. A cache registered there is cleaned up with the owning feature.

A cache should have a clear answer to:

- what is the key?
- what invalidates an entry?
- what happens on feature reload?
- can stale values be tolerated?
- is this cache local or network-authoritative?

Do not use a cache as an undeclared cross-feature communication channel.

## Persistence boundaries

Keep persistence concerns behind normal repositories/services inside the feature:

```text
ProfilesFeature
├── ProfileRepository
├── ProfileService
└── ProfileCache
```

Publish a capability such as `PlayerProfileApi` if other features need profiles. Do not expose the repository or database handle unless that is intentionally the contract.

## Shutdown

Data ingress is part of lifecycle quiescing. For custom clients not managed by the framework, close them only after callers can no longer reach the service that uses them.

## Testing

Test recreation with warm caches and in-flight data operations. The second enable cycle is where leaked handles and stale callbacks are most likely to become visible.
