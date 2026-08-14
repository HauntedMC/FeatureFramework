# Data and Caching

Data clients and caches should have a clear owner just like listeners and tasks.

## DataProvider integration

Add the platform integration artifact and attach `PaperDataProviderContributor` or
`VelocityDataProviderContributor` with the host builder's `contribute(...)` method.

Those factories create one `DataProviderResources` for each feature scope. The manager is bound to the feature name and participates in lifecycle quiescing/cleanup automatically.

`DataProviderResources` can own:

- database providers/connections;
- typed `DataAccess` instances;
- Redis messaging providers/access;
- ORM contexts.

Paper resolves `DataProviderAPI` through Bukkit's service registry. Velocity resolves the plugin id `dataprovider` and expects its instance to expose `DataProviderApiSupplier`.

See the complete [Paper DataProvider example](../../examples/paper/08-dataprovider/README.md) and [Velocity DataProvider example](../../examples/velocity/08-dataprovider/README.md).

## DataRegistry integration

DataRegistry is a separate typed resource extension. Attach it with:

```java
.contribute(PaperDataRegistryContributor.create(() -> registryApi))
```

Use the corresponding Velocity contributor on Velocity; both also provide `optional(...)` for an
optional host-plugin dependency.

Features remain on the normal platform base and implement `PaperDataRegistryAccess` or
`VelocityDataRegistryAccess` when they need the convenience accessors/readiness context. Declare
`DataRegistryResources.class` in `requiresResourceExtensions` so missing host wiring fails at the
feature boundary.

See the [Paper DataRegistry example](../../examples/paper/09-dataregistry/README.md) and [Velocity DataRegistry example](../../examples/velocity/09-dataregistry/README.md).

## Shared infrastructure vs raw clients

If several features need the same logical backend, prefer exposing the behavior through a capability instead of handing every feature a raw client:

```text
RedisFeature
  owns Redis client
  provides NetworkBusApi
       ├──> QueueFeature
       └──> ModerationFeature
```

This keeps backend-specific code in one place and lets consumers depend on a contract.

## Caches

Both platform resource scopes include a `FeatureCacheManager`. It creates normalized feature-owned cache directories and stops accepting access when the owning scope is quiesced. JSON `FileCacheStore` values support explicit TTLs and corruption-tolerant atomic writes.

Before adding a cache, be clear about its key, invalidation rule, lifetime, and whether stale values are acceptable. Do not use a cache as hidden cross-feature state.

Write the policy down as a small matrix before implementing it:

| Cached value | Authority | Freshness | Miss/failure behavior |
|---|---|---|---|
| player-facing list | database | short bounded TTL | async refresh; report unavailable if authority fails |
| join hint/count | derived projection | longer bounded TTL | omit hint or use documented last-known value |
| proxy routing health | message snapshot | seconds | fallback or deny according to explicit policy |
| permission/reward decision | normally not a disk cache | strict | consult authority; never silently trust old data |

Use an in-memory immutable snapshot for hot event paths and a file cache only when restart continuity is useful. A restored file value still needs a domain timestamp/freshness check; the file TTL alone does not prove the upstream observation is current.

## Persistence code

Repositories and data services can remain normal classes inside a feature:

```text
ProfilesFeature
├── ProfileRepository
├── ProfileService
└── ProfileCache
```

If another feature needs profile behavior, expose a `PlayerProfileApi` capability instead of the repository or database handle.

See the [persistent Paper example](../../examples/paper/11-persistent-contract-board/README.md) for authoritative SQL plus hot/disk cache layers, and the [Velocity rollout example](../../examples/velocity/11-adaptive-rollout-router/README.md) for a Redis-maintained read model used synchronously on a connection path.
