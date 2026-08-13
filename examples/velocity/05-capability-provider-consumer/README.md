# 05 — Capability provider and consumer

This example shows a complete proxy-side capability flow.

`NetworkPlayerApi` is platform-neutral. `NetworkDirectoryFeature` implements it using the current `ProxyServer`, publishes it with `services().registerService(...)`, and its definition declares the provided capability. `NetworkCommandsFeature` declares the capability as required and resolves it with `requireCapability(...)`.

The consumer does not need to know whether the provider uses Velocity state, Redis, a database, or something else.
