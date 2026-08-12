# 04 — Dependencies and integrations

Proxy applications often contain network concerns that should not all become global services.

Examples:

- `Queue` can require `ServerDirectory` as a feature dependency.
- `DiscordBridge` can be optional to moderation announcements.
- an integration feature can declare the external plugin it needs with `requiresPlugins(...)` where platform plugin discovery is part of its contract.
- reusable contracts such as `NetworkPlayerApi` should normally be capabilities instead of hard-coding a provider feature name.

`FeatureDefinitions.java` makes these relationships visible at composition time.
