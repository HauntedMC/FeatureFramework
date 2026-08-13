# 05 — Capability provider and consumer

This directory contains the entire provider/consumer flow:

```text
GreetingProviderFeature
    provides GreetingApi
           |
           v
GreetingConsumerFeature
    requires GreetingApi
```

1. `GreetingApi.java` is the small contract shared by both features.
2. `GreetingProviderFeature` declares `providesCapabilities = GreetingApi.class`.
3. `GreetingProviderFeature.initialize()` registers the implementation with `services().registerService(...)`.
4. `GreetingConsumerFeature` declares `requiresCapabilities = GreetingApi.class`.
5. `GreetingConsumerFeature.initialize()` resolves it with `requireCapability(...)`.
6. `MyPlugin.java` supplies the generated catalog to the host.

The provider owns the registered service. When the provider stops, the framework removes it before provider state is torn down.

The consumer depends on `GreetingApi`, not `GreetingProviderFeature`. That is the reason to use a capability instead of `requiresFeatures("GreetingProvider")`.
