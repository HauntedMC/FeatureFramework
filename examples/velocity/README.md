# Velocity examples

The Velocity track mirrors the architectural progression of the Paper track while preserving proxy-native behavior.

| Level | Example | Teaches |
|---|---|---|
| 1 | [01-simple-feature](01-simple-feature/README.md) | feature, definition, Velocity host |
| 2 | [02-owned-resources](02-owned-resources/README.md) | feature-owned proxy resources |
| 3 | [03-config-and-messages](03-config-and-messages/README.md) | defaults and reload policy |
| 4 | [04-dependencies-and-integrations](04-dependencies-and-integrations/README.md) | feature/plugin relationships |
| 5 | [05-capability-provider-consumer](05-capability-provider-consumer/README.md) | reusable cross-feature contracts |
| 6 | [06-multi-feature-plugin](06-multi-feature-plugin/README.md) | realistic proxy composition |
| 7 | [07-advanced-lifecycle](07-advanced-lifecycle/README.md) | concurrency, reload, cleanup |

Velocity host lifecycle operations execute directly on the caller; FeatureFramework does not invent a Bukkit primary-thread model. See [Threading](../../docs/THREADING.md).
