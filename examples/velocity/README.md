# Velocity examples

Each directory is self-contained at the application-source level and includes its own real Velocity bootstrap plus every custom feature/contract it references.

| Level | Example | What it teaches |
|---|---|---|
| 1 | [01-simple-feature](01-simple-feature/README.md) | annotated feature, generated catalog, ready-to-use Velocity host |
| 2 | [02-owned-resources](02-owned-resources/README.md) | owned listener/task resources |
| 3 | [03-config-and-messages](03-config-and-messages/README.md) | defaults and reload policy |
| 4 | [04-dependencies-and-integrations](04-dependencies-and-integrations/README.md) | required/optional features and plugin dependencies |
| 5 | [05-capability-provider-consumer](05-capability-provider-consumer/README.md) | publishing/consuming a capability |
| 6 | [06-multi-feature-plugin](06-multi-feature-plugin/README.md) | larger proxy composition |
| 7 | [07-advanced-lifecycle](07-advanced-lifecycle/README.md) | concurrency, recreation, manually owned clients |
| 8 | [08-dataprovider](08-dataprovider/README.md) | `DataProviderResources` and a host resource contributor |
| 9 | [09-dataregistry](09-dataregistry/README.md) | `VelocityDataRegistryAccess`, DataRegistry, identity readiness |
| 10 | [10-network-operations](10-network-operations/README.md) | control-plane command, lifecycle operations, production feature graph |
| 11 | [11-adaptive-rollout-router](11-adaptive-rollout-router/README.md) | Redis-driven subsystem: health snapshots, restart cache, canary/fallback policy, listener, command, config/messages, capability |

Every example declares metadata beside the feature with `@FeatureDeclaration`; the bootstrap uses
`@GenerateFeatureCatalog` and generated `BuiltInFeatures.collection()`. Velocity host lifecycle operations run directly on the caller; FeatureFramework does not create a Bukkit-style main thread. See [Threading](../../docs/THREADING.md).

## For developers evaluating this on a large network

Once you know the feature mental model, [11-adaptive-rollout-router](11-adaptive-rollout-router/README.md) demonstrates a latency-sensitive proxy application:

```text
DataProvider Redis subscription → concurrent freshness-bounded snapshot
                                      ├── deterministic canary/failover policy
                                      ├── cache-only ServerPreConnect listener
                                      ├── reload-safe routing capability
                                      └── localized operations command + expiry task
```

Its README makes thread handoff, stale-data policy, subscription ownership, and generation replacement explicit.
