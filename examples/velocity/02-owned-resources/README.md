# 02 — Feature-owned Velocity resources

`ProxyPlugin.java` provides the complete host bootstrap. `OwnedResourcesFeature.java` registers a real Velocity listener and a repeating task through its feature resource scope.

```text
ActivityFeature
  -> FeatureListenerManager -> PostLoginEvent listener
  -> FeatureTaskManager     -> 30-second player-count task
```

Both are removed/cancelled when the feature stops. `disable()` therefore does not manually unregister them.

Use native `ProxyServer` APIs for operations that do not create a long-lived owned resource. If you register something long-lived outside the framework managers, keep and execute its cleanup path yourself.
