# 05 — Capability provider and consumer

This directory contains the complete capability flow:

```text
NetworkDirectoryFeature
    provides NetworkPlayerApi
           |
           v
NetworkCommandsFeature
    requires NetworkPlayerApi
```

`NetworkPlayerApi` is deliberately small. `NetworkDirectoryFeature` implements it using `ProxyServer` and registers the implementation with the feature service manager. `NetworkCommandsFeature` only knows the contract and resolves it with `requireCapability(...)`.

`ProxyPlugin.java` composes both definitions into a normal ready-to-use `VelocityFeatureHost`.

The provider could later move from local proxy state to Redis or another backend without changing consumers as long as the capability contract remains the same.
