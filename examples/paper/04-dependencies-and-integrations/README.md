# 04 — Dependencies and integrations

Definitions are where application relationships should be visible.

`FeatureDefinitions.java` shows four cases:

- `requiresFeatures` — hard lifecycle dependency on another feature;
- `optionallyUsesFeatures` — enhancement when another feature exists;
- `requiresPlugins` — external Paper plugin requirement localized to one feature;
- `startupOrder` — deterministic ordering hint, not a replacement for real dependency declarations.

If the consumer really needs an interface rather than one named implementation, use a capability instead. That is the next example.
