# 02 — Feature-owned Velocity resources

`OwnedResourcesFeature.java` registers a real Velocity listener and repeating task through the feature resource scope.

The listener and task are automatically removed/cancelled when the feature stops. Use `getContext().proxy()` for native proxy operations that do not need their own managed lifetime.

If you register a long-lived resource directly through Velocity or a third-party API, keep its cleanup path explicit.
