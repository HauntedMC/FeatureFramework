# 04 — Dependencies and integrations

This directory contains the full example graph:

```text
Profiles ---------> Chat
DiscordBridge ----> Chat (optional)
PlaceholderAPI ---> Placeholders (external plugin)
```

The annotations on the four feature classes are the important part. Relationships live beside the implementation,
and the compiler creates the catalog used by `MyPlugin`.

- `requiresFeatures = "Profiles"` means Chat must not run without the named feature and gives the graph a real lifecycle dependency.
- `optionallyUsesFeatures = "DiscordBridge"` records an enhancement relationship without making startup depend on it.
- `requiresPlugins = "PlaceholderAPI"` keeps an external plugin requirement on the feature that needs it.
- `startupPhase = FeatureStartupPhase.DEFERRED` starts this non-critical integration after the core application. A phase is only an ordering hint; it should not replace a real dependency declaration.

All four example feature classes are included so there are no unexplained custom references. PlaceholderAPI itself is intentionally external: the point of `requiresPlugins` is to describe a real separately installed plugin.

If Chat needed a reusable API rather than the identity/lifecycle of `Profiles`, a capability would be the better relationship. The next example shows that.
