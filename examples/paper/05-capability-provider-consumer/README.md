# 05 — Capability provider and consumer

Capabilities let a feature depend on a contract instead of a named implementation.

This example contains the complete framework-specific flow:

1. `GreetingApi` defines the contract.
2. `Definitions.provider()` declares `providesCapabilities(GreetingApi.class)`.
3. `GreetingProviderFeature` registers the implementation with `services().registerService(...)`.
4. `Definitions.consumer()` declares `requiresCapabilities(GreetingApi.class)`.
5. `GreetingConsumerFeature` resolves it with `requireCapability(...)`.

The service is owned by the provider feature and is removed when that feature stops.
