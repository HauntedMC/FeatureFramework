# FeatureFramework Velocity

`featureframework-velocity` is the Velocity adapter for FeatureFramework.

Start with the [Velocity examples](../examples/velocity/README.md). Dependency, GitHub Packages, and shading setup are in the [root README](../README.md).

## Main types

- `VelocityFeature<P>` — managed Velocity feature base.
- `VelocityFeatureContext<P>` — plugin, `ProxyServer`, config, localization, logger, resources, capabilities, and services for one feature.
- `VelocityFeatureResources` — owned tasks, commands, listeners, data, caches, and services.
- `VelocityFeatureHost` — configurable host façade for normal and integration-rich proxy composition.

Add `featureframework-velocity-integrations` for DataProvider and DataRegistry adapters, then attach
their scoped contributors with the host builder's `contribute(...)` method.

Velocity host lifecycle operations execute directly on the caller; FeatureFramework does not add a Bukkit-style main thread. See [Threading](../docs/THREADING.md).

As on Paper, long-lived tasks, listeners, commands, caches, and services should have one clear owning feature whenever possible.
