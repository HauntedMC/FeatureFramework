# 02 — Feature-owned Velocity resources

`VelocityFeatureResources` owns the proxy-side task, command, listener, cache, data, and feature-service managers.

The same rule as Paper applies: if a resource exists because one feature is enabled, register it through that feature's owned manager when an adapter exists. Cleanup then follows feature lifetime instead of plugin-global lifetime.

Use `getContext().proxy()` for native `ProxyServer` operations that are not wrapped by a framework adapter. If you directly register a resource outside the owned managers, make its unregister/close path explicit.

See the framework classes under `featureframework-velocity/.../lifecycle` for the exact scheduling/listener overloads supported by your version.
