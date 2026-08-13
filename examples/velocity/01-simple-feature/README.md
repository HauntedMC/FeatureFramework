# 01 — Simple Velocity feature

This is a complete minimal Velocity shape:

- `ProxyPlugin.java` is a real Velocity `@Plugin` with constructor injection and initialize/shutdown events.
- `ProxyWelcomeFeature.java` is one managed feature.

The bootstrap declares `@GenerateFeatureCatalog`; the feature declares `@FeatureDeclaration`. The compiler
generates `BuiltInFeatures`, which the bootstrap passes to `VelocityFeatureHost`. Shutdown stops the same host.

The feature gets native proxy access from `getContext().proxy()` while the host still owns its lifecycle.
