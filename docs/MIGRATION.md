# Application migration guide

Use this guide when moving an existing Paper or Velocity application onto FeatureFramework, or when
removing duplicated framework infrastructure from an application module.

## Keep in the application

- plugin bootstrap and platform metadata;
- the explicit `FeatureCollection`/catalog of concrete features;
- domain capability contracts, persistence entities, and integration adapters;
- application-specific commands, configuration defaults, and operator messaging.

## Move to FeatureFramework

- feature contexts, scopes, descriptors, registries, graph loading, and lifecycle coordination;
- configuration/localization storage, capability publication, command models, and resource cleanup;
- reusable platform adapters for Paper or Velocity;
- DataProvider resource assembly and optional DataRegistry discovery/gate plumbing.

Concrete features should extend `PaperDataProviderFeature` or `VelocityDataProviderFeature` directly
when they use the standard data integration. Use `PaperFeature` or `VelocityFeature` for features
without that dependency. Do not recreate a local base-feature, context, host, or manager hierarchy.

## Migration steps

1. Add the required framework platform dependency and shade it into the application artifact.
2. Replace reflective/scanned feature discovery with typed `FeatureDefinition` entries collected in a
   `FeatureCollection`.
3. Replace application-owned feature loading and lifecycle managers with `PaperFeatureHost`,
   `VelocityFeatureHost`, or a platform host composition.
4. Migrate concrete features to the framework platform base and receive dependencies through their
   typed context rather than static application lookups.
5. Register tasks, listeners, commands, services, caches, and data connections through the scoped
   framework resources so reload and disable can clean them up.
6. Keep domain capability interfaces in the application API; publish them through the framework
   capability registry instead of exposing feature implementations.
7. Add architecture tests that prevent reintroducing a local framework `host`, generic `util`, or
   base-feature package.

## Verification

Run the application build from a clean checkout:

```shell
./mvnw clean verify
```

For FeatureFramework itself, the optional real-platform gate is:

```shell
./mvnw -Pplatform-acceptance clean verify
```

The acceptance profile compiles and boots independent Paper and Velocity plugins against pinned
runtimes. It does not require Docker or an external database.
