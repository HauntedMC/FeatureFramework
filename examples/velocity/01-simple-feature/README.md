# 01 — Simple Velocity feature

This is a complete minimal Velocity shape:

- `ProxyPlugin.java` is a real Velocity `@Plugin` with constructor injection and initialize/shutdown events.
- `ProxyWelcomeFeature.java` is one managed feature.

The bootstrap creates one `FeatureDefinition`, puts it in a `FeatureCollection`, builds `VelocityFeatureHost`, and starts it. Shutdown stops the same host.

The feature gets native proxy access from `getContext().proxy()` while the host still owns its lifecycle.
