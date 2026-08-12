# FeatureFramework Paper

The Paper module is the **Bukkit-side platform adapter** for FeatureFramework. Use it to build a Paper plugin as independently managed features while retaining native Paper/Bukkit APIs where needed.

## Start here

1. Read the root [FeatureFramework overview](../README.md).
2. Follow [`examples/paper/01-simple-feature`](../examples/paper/01-simple-feature/README.md).
3. Learn [lifecycle/resource ownership](../docs/concepts/LIFECYCLE-AND-RESOURCES.md).
4. Continue through the [Paper example path](../examples/paper/README.md).

## Main types

- `PaperFeature<P, D>` — base class for a managed Paper feature.
- `PaperFeatureContext<P, D>` — feature-scoped access to plugin, config, localization, resources, logging, capabilities, and services.
- `PaperFeatureResources<D>` — ownership scope for tasks, commands, listeners, data, caches, GUIs, and feature services.
- `PaperFeatureHost` — runs a collection of definitions.
- `PaperFeatureHostComposition` — lower-level composition when the application needs custom host policy/resources.

## Rule of thumb

If a listener, task, command, cache, GUI, or service belongs to one feature, register it through that feature's resource scope. That gives the host enough ownership information to disable/reload the feature safely.

## Paper threading

Host lifecycle operations obey Bukkit primary-thread semantics. Do not assume that means every task is synchronous; task APIs still expose synchronous/asynchronous scheduling explicitly. Read [THREADING.md](../docs/THREADING.md).

## Dependency

```xml
<dependency>
  <groupId>nl.hauntedmc.featureframework</groupId>
  <artifactId>featureframework-paper</artifactId>
  <version>RELEASE_VERSION</version>
</dependency>
```

See the root README for GitHub Packages and shading requirements.
