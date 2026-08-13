# 04 — Dependencies and integrations

`FeatureDefinitions.java` focuses on the dependency declaration itself; the example feature implementations are omitted.

`Queue` requires `ServerDirectory` and can optionally use `DiscordBridge`. The same definition API can declare an external plugin with `requiresPlugins(...)` when a feature genuinely depends on one.

Use a capability instead when the consumer needs a reusable contract rather than a specific provider feature.
