# 02 — Feature-owned Paper resources

This example is complete on its own:

- `MyPlugin.java` creates the feature definition and host.
- `ActivityFeature.java` registers a listener and repeating task through its resource scope.

The important part is what **isn't** in `disable()`: there is no listener unregister and no task cancellation. Because both resources were registered through `PaperFeatureResources`, the framework owns their cleanup.

```text
MyPlugin
  -> PaperFeatureHost
      -> ActivityFeature
          -> FeatureListenerManager -> JoinListener
          -> FeatureTaskManager     -> repeating heartbeat
```

Use the same ownership rule for commands, caches, GUIs, data resources, and published services whenever FeatureFramework supplies a managed adapter.
