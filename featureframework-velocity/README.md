# FeatureFramework Velocity

The Velocity module is the proxy platform adapter for FeatureFramework. It uses the same feature definitions, lifecycle model, dependency graph, capability model, configuration approach, and service ownership as the Paper adapter while preserving Velocity-native execution and APIs.

## Start here

1. Read the root [FeatureFramework overview](../README.md).
2. Follow [`examples/velocity/01-simple-feature`](../examples/velocity/01-simple-feature/README.md).
3. Learn [lifecycle/resource ownership](../docs/concepts/LIFECYCLE-AND-RESOURCES.md).
4. Continue through the [Velocity example path](../examples/velocity/README.md).

## Main types

- `VelocityFeature<P, D>` — base class for a managed Velocity feature.
- `VelocityFeatureContext<P, D>` — feature-scoped plugin/config/localization/resources/logging/services plus native `ProxyServer` access.
- `VelocityFeatureResources<D>` — feature ownership scope for Velocity resources.
- `VelocityFeatureHost` — runs a collection of definitions.
- `VelocityFeatureHostComposition` — lower-level host composition for custom application policy/resources.

## Rule of thumb

Platform registrations should have one obvious owner. If a task, command, listener, cache, or service exists because a feature is enabled, prefer the feature-owned adapter so it disappears with that feature.

## Velocity execution

Velocity host lifecycle operations execute directly on the caller; the framework does not invent a Bukkit-style main-thread hop. Read [THREADING.md](../docs/THREADING.md) before coordinating lifecycle changes with asynchronous work.

## Dependency

```xml
<dependency>
  <groupId>nl.hauntedmc.featureframework</groupId>
  <artifactId>featureframework-velocity</artifactId>
  <version>RELEASE_VERSION</version>
</dependency>
```

See the root README for GitHub Packages and shading requirements.
