# 06 — Multi-feature Paper plugin

This is a complete example of a small application graph rather than a list of undefined feature names.

```text
ProfilesFeature
  provides PlayerProfileApi
       ├──> ChatFeature
       └──> ModerationFeature

PlaceholderBridgeFeature
  requires PlaceholderAPI
  optionally uses PlayerProfileApi
```

`MyPlugin.java` remains tiny. `Features.java` is the composition map. Every feature implementation and `PlayerProfileApi` contract is present in this directory.

Notice that Chat and Moderation require the **capability**, not the provider feature by name. They care about profile behavior, so another provider could replace `ProfilesFeature` without changing the consumers.

This is the intended larger-plugin structure:

- host/bootstrap = application composition;
- feature = lifecycle boundary;
- ordinary classes inside a feature = implementation details;
- capabilities/internal services = explicit cross-feature contracts.
