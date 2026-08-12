# 05 — Capability provider and consumer

Capabilities decouple **what a feature needs** from **which feature implements it**.

This example models a `GreetingApi` contract. The provider definition declares `providesCapabilities(GreetingApi.class)` and the consumer declares `requiresCapabilities(GreetingApi.class)`.

Inside the consumer, `requireCapability(GreetingApi.class)` resolves the current provider. Required capability relationships also allow the framework's manifest discovery to derive graph dependencies.

In a real provider, publish the capability implementation through the feature-owned service/capability publication path during initialization so its availability follows the feature lifecycle. The exact publication API depends on the service contract/host composition you use; keep the interface itself small and platform-independent where possible.
