# FeatureFramework Velocity

`featureframework-velocity` is the Velocity adapter for FeatureFramework.

Start with the [Velocity examples](../examples/velocity/README.md). Dependency, GitHub Packages, and shading setup are in the [root README](../README.md).

## Main types

- `VelocityFeature<P, D>` — managed Velocity feature base.
- `VelocityFeatureContext<P, D>` — plugin, `ProxyServer`, config, localization, logger, resources, capabilities, and services for one feature.
- `VelocityFeatureResources<D>` — owned tasks, commands, listeners, data, caches, and services.
- `VelocityFeatureHost` — ready-to-use host for normal proxy composition.
- `VelocityFeatureHostComposition` — lower-level composition when custom data/resources or host policy are needed.

Velocity host lifecycle operations execute directly on the caller; FeatureFramework does not add a Bukkit-style main thread. See [Threading](../docs/THREADING.md).

As on Paper, long-lived tasks, listeners, commands, caches, and services should have one clear owning feature whenever possible.
