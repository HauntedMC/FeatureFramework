# FeatureFramework Paper

`featureframework-paper` is the Paper/Bukkit adapter for FeatureFramework.

Start with the [Paper examples](../examples/paper/README.md). Dependency, GitHub Packages, and shading setup are in the [root README](../README.md).

## Main types

- `PaperFeature<P>` — managed Paper feature base.
- `PaperFeatureContext<P>` — plugin, config, localization, logger, resources, capabilities, and services for one feature.
- `PaperFeatureResources` — owned tasks, commands, listeners, data, caches, GUIs, and services.
- `PaperFeatureHost` — configurable host façade for normal and integration-rich plugin composition.

Add `featureframework-paper-toolkit` for UI helpers and `featureframework-paper-integrations` for
DataProvider, DataRegistry, PlaceholderAPI, ViaVersion, and packet adapters. Attach scoped facilities
with the host builder's `contribute(...)` method.

Paper host lifecycle operations follow Bukkit primary-thread rules. Task scheduling can still be synchronous or asynchronous, so normal Bukkit thread-safety rules still apply. See [Threading](../docs/THREADING.md).

The key ownership rule is simple: if a long-lived registration exists because one feature is enabled, prefer registering it through that feature's resource scope when an adapter exists.
