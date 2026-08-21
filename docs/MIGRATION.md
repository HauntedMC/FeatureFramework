# Application migration guide

Use this guide when moving an existing Paper or Velocity application onto FeatureFramework, or when
removing duplicated framework infrastructure from an application module.

## Migrating to 1.6.0

Version 1.6.0 is a deliberate clean boundary release. It removes HauntedMC-private APIs from the
public framework with no deprecated aliases or compatibility wrappers.

- `nl.hauntedmc.featureframework.api.network.ServerId` moved to the private
  `nl.hauntedmc.proxyfeatures.contracts.network.ServerId` contract.
- `DataProviderConnections` and the implicit `createPlayerOrmContext`, `createSystemOrmContext`, and
  Redis convenience overloads were removed. Applications must own their connection names and call the
  explicit APIs, for example `createMySqlOrmContext("playerOrm", "my_player_data", Entity.class)` and
  `registerRedisMessagingProvider("redis", "my_redis")`. HauntedMC consumers use
  `HauntedDataConnections` from `proxyfeatures-contracts`.
- `featureframework-data-audit` moved to ProxyFeatures support code.
- PacketEvents packet wrappers, `PlayerDataFiles`, `ServerActiveClock`, and `ConnectionLogHelper` moved
  to the owning private platform projects. They have no general public replacement.
- The ServerFeatures-specific scoreboard/glow runtime (`ScoreboardManager`, `ScoreboardListener`, and
  `PaperUiRuntime`) moved to ServerFeatures. Generic Paper UI primitives remain in the Paper toolkit.
- `FeatureCommandView` was removed. Consumer command front ends own their presentation and wording;
  `FeatureCommandModel` and `FeatureOperationMessages` remain the framework contracts.

Upgrade direct consumers by removing the deleted imports and adding their own product dependencies or
implementations. Do not retain copies under the former FeatureFramework packages.

## Migrating to 1.5.0

Version 1.5.0 adds the optional `featureframework-theme-api` module and host-level programmatic themes. Existing host
builders and localization constructors remain source-compatible and behave as before when no theme is registered.

To adopt themes, add a theme implementation to the host builder with `theme(...)` or `themes(...)`, then use
`<theme-id:item-id>` references in newly generated defaults. The same immutable theme registry is propagated to all
feature localization instances.

Theme libraries that only construct theme objects should depend on `featureframework-theme-api`. Applications already
using a Paper or Velocity host receive the API transitively but should declare it directly when their source code uses
theme API types. See [Programmatic message themes](guides/THEMES.md) for the complete contract.

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

Concrete features always extend `PaperFeature` or `VelocityFeature` directly. Install DataProvider,
DataRegistry, UI, or third-party behavior through host resource contributors and declare required
resource-extension types in `@FeatureDeclaration`. Do not recreate a local base-feature, context,
host, lifecycle tracker, or manager hierarchy.

## Descriptor terminology

FeatureFramework exposes two intentionally different descriptor types for distinct responsibilities:

- `nl.hauntedmc.featureframework.loader.ResolvedFeatureDefinition<F, C>` is the host construction descriptor.
  It contains the concrete feature type, constructor, and required/optional/plugin dependencies.
- `nl.hauntedmc.featureframework.api.feature.FeatureMetadata` is implementation-free public catalog
  metadata. It is safe to expose to consumers of `FeatureFrameworkApi`.

Use `@FeatureDeclaration` on each concrete feature and `@GenerateFeatureCatalog` on the bootstrap. The
processor generates the `FeatureDefinition`/`FeatureCollection` implementation. Manual composition remains an
advanced option for inventories that are genuinely dynamic.

## Migration steps

1. Add the required framework platform dependency and shade it into the application artifact.
2. Declare each concrete feature with `@FeatureDeclaration` and add `@GenerateFeatureCatalog` to the bootstrap.
   Configure `featureframework-processor` explicitly in the compiler's annotation processor path.
3. Replace application-owned feature loading and lifecycle managers with `PaperFeatureHost`,
   or `VelocityFeatureHost`.
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

This release intentionally removes obsolete framework accessors. Migrate feature code to `plugin()`,
`logger()`, `resources()`, and `localization()`.
