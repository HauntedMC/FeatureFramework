# Paper (Bukkit-side) examples

Follow these in order if FeatureFramework is new to you.

| Level | Example | Teaches |
|---|---|---|
| 1 | [01-simple-feature](01-simple-feature/README.md) | feature class, definition, host |
| 2 | [02-owned-resources](02-owned-resources/README.md) | listener/task ownership and cleanup |
| 3 | [03-config-and-messages](03-config-and-messages/README.md) | feature-scoped defaults and reload policy |
| 4 | [04-dependencies-and-integrations](04-dependencies-and-integrations/README.md) | required/optional features and external plugins |
| 5 | [05-capability-provider-consumer](05-capability-provider-consumer/README.md) | reusable cross-feature contracts |
| 6 | [06-multi-feature-plugin](06-multi-feature-plugin/README.md) | realistic application composition |
| 7 | [07-advanced-lifecycle](07-advanced-lifecycle/README.md) | soft reload vs recreation, async work, cleanup |

Paper lifecycle changes obey Bukkit primary-thread semantics. Async work still needs normal Bukkit thread-safety discipline. See [Threading](../../docs/THREADING.md).
