# Application migration guide

Use this guide when moving an existing Paper or Velocity application onto FeatureFramework, or when
removing duplicated framework infrastructure from an application module.

## Keep in the application

- plugin bootstrap and platform metadata;
- concrete feature classes and their `@FeatureDeclaration` metadata;
- domain capability contracts, persistence entities, and integration adapters;
- application-specific commands, configuration defaults, and operator messaging.

## Move to FeatureFramework

- feature contexts, scopes, construction descriptors, registries, graph loading, and lifecycle
  coordination;
- configuration/localization storage, capability publication, command models, and resource cleanup;
- reusable platform adapters for Paper or Velocity;
- DataProvider resource assembly and optional DataRegistry discovery/gate plumbing.

Concrete features should extend `PaperDataProviderFeature` or `VelocityDataProviderFeature` directly
when they use the standard data integration. Use `PaperFeature` or `VelocityFeature` for features
without that dependency. Do not recreate a local base-feature, context, host, lifecycle tracker, or
manager hierarchy.

## Descriptor terminology

FeatureFramework exposes two intentionally different descriptor types for distinct responsibilities:

- `nl.hauntedmc.featureframework.loader.FeatureDescriptor<F, C>` is the host construction descriptor.
  It contains the concrete feature type, constructor, and required/optional/plugin dependencies.
- `nl.hauntedmc.featureframework.api.feature.FeatureDescriptor` is implementation-free public catalog
  metadata. It is safe to expose to consumers of `FeatureFrameworkApi`.

Use `@FeatureDeclaration` on each concrete feature and `@GenerateFeatureCatalog` on the bootstrap. The
processor generates the `FeatureDefinition`/`FeatureCollection` implementation. Manual composition remains an
advanced option for inventories that are genuinely dynamic.

## Migration steps

1. Add the required framework platform dependency and shade it into the application artifact.
2. Declare each concrete feature with `@FeatureDeclaration` and add `@GenerateFeatureCatalog` to the bootstrap.
   Configure `featureframework-processor` explicitly in the compiler's annotation processor path.
3. Replace application-owned feature loading and lifecycle managers with `PaperFeatureHost`,
   `VelocityFeatureHost`, or a platform host composition.
4. Migrate concrete features to the framework platform base and receive dependencies through their
   typed context rather than static application lookups.
5. Register tasks, listeners, commands, services, caches, and data connections through the scoped
   framework resources so reload and disable own their complete cleanup lifecycle.
6. Keep domain capability interfaces in the application API; publish them through the framework
   capability registry instead of exposing feature implementations.
7. Remove application copies of framework host, scope, lifecycle, configuration/localization, and
   platform-adapter infrastructure after the migrated build and tests are green.
8. Add architecture tests that prevent reintroducing a local framework `host`, generic manager, or
   base-feature hierarchy.

## Threading during migration

Do not add application-side thread marshaling around host operations. Paper host lifecycle operations
already have synchronous primary-thread semantics, including calls originating from asynchronous
threads. Velocity lifecycle operations execute directly without a synthetic main-thread hop. Keep
long-running application work on the appropriate platform scheduler and use the feature task manager so
it is cancelled during reload/shutdown.

Do not acquire an application graph lock and then invoke a Paper host operation that may cross to the
primary thread. FeatureFramework itself enters its execution-affinity boundary before acquiring the
shared lifecycle lock; application code should preserve the same ordering principle.

## Verification

Run the application build from a clean checkout:

```shell
./mvnw clean verify
```

For FeatureFramework itself, the real-platform gate is:

```shell
./mvnw -Pplatform-acceptance clean verify
```

The acceptance profile compiles and boots independent Paper and Velocity plugins against pinned
runtimes and verifies graph reload plus feature-owned task/listener/command/service cleanup. It does not
require Docker or an external database.

FeatureFramework 2.x intentionally removes legacy accessors. Migrate feature code to `plugin()`,
`logger()`, `resources()`, and `localization()`.
