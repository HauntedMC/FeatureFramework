# Paper examples

Each directory below is self-contained at the application-source level: all custom classes referenced by that example are included in the same directory.

| Level | Example | What it teaches |
|---|---|---|
| 1 | [01-simple-feature](01-simple-feature/README.md) | annotated feature, generated catalog, ready-to-use host |
| 2 | [02-owned-resources](02-owned-resources/README.md) | listener/task ownership and automatic cleanup |
| 3 | [03-config-and-messages](03-config-and-messages/README.md) | feature defaults and reload policy |
| 4 | [04-dependencies-and-integrations](04-dependencies-and-integrations/README.md) | feature dependencies, optional features, external plugins |
| 5 | [05-capability-provider-consumer](05-capability-provider-consumer/README.md) | publishing and consuming a capability |
| 6 | [06-multi-feature-plugin](06-multi-feature-plugin/README.md) | realistic multi-feature composition |
| 7 | [07-advanced-lifecycle](07-advanced-lifecycle/README.md) | recreation, async work, manually owned resources |
| 8 | [08-dataprovider](08-dataprovider/README.md) | `FeatureDataManager`, DataProvider, custom host composition |
| 9 | [09-dataregistry](09-dataregistry/README.md) | `PaperDataRegistryFeature`, DataRegistry, identity readiness |

Every example declares metadata beside the feature with `@FeatureDeclaration`; the bootstrap uses
`@GenerateFeatureCatalog` and generated `BuiltInFeatures.collection()`. Start with 01 if the framework is new to you.

Paper host lifecycle operations follow Bukkit primary-thread semantics. Async tasks still require normal Bukkit thread-safety discipline; see [Threading](../../docs/THREADING.md).
