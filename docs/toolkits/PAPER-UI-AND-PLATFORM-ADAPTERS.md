# Paper UI and Platform Adapters

The Paper module includes more than the feature host. It contains Paper-specific adapters for areas that cannot live in the platform-neutral shared module.

Depending on the framework version, these areas include:

- inventory/menu GUI support;
- packets;
- registries;
- clocks/time helpers;
- previews;
- toast/UI integrations;
- Brigadier/Paper command integration.

## GUI ownership

`PaperFeatureResources` owns a `FeatureGUIManager`, and that manager participates in lifecycle cleanup. If a menu exists only because a feature is enabled, keep its creation/registration under that feature's resource scope.

Do not keep plugin-global references to feature-owned menus after feature recreation.

## Time helpers

Use `BukkitTime` for framework scheduling APIs that accept a Minecraft time quantity. It provides factories such as `ticks`, `milliseconds`, `seconds`, `minutes`, and `hours`, keeping unit conversions visible.

## Native APIs are still valid

FeatureFramework does not try to wrap every Paper API. Use native APIs directly when they are the clearest choice. The architectural rule is ownership, not “everything must have a wrapper.”

When a native registration/resource outlives a method call, decide who owns its cleanup.

## Platform-neutral code

Keep Paper types at the edge where possible:

```text
Paper listener/command/UI
        |
        v
feature domain service
        |
        v
platform-neutral models/contracts
```

This makes behavior easier to test and, where useful, lets Paper and Velocity features share domain contracts.
