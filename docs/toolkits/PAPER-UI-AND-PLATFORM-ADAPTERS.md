# Paper UI and Platform Adapters

The Paper module contains platform-specific helpers that cannot live in the shared module, including inventory/menu UI support, command/Brigadier integration, packets, registries, time/clock helpers, previews, and toast/UI adapters.

## GUI ownership

`PaperFeatureResources` owns a `FeatureGUIManager`. Menus created for one feature should stay under that feature's resource scope so they are shut down with it.

Avoid storing plugin-global references to a feature-owned menu after the feature can be reloaded or recreated.

## Time helpers

FeatureFramework scheduling APIs use `BukkitTime` where a Minecraft time quantity is expected:

```java
BukkitTime.ticks(20);
BukkitTime.seconds(5);
BukkitTime.minutes(1);
```

This keeps units explicit instead of spreading manual tick conversions through feature code.

## Native Paper APIs

FeatureFramework does not need to wrap every Paper API. Use native APIs when they are clearer. The important question is who owns any long-lived registration or resource and how it is cleaned up.

Keep platform code near the edge of the feature where practical so domain services and capability contracts remain easy to test and reuse.
