# 04 — Dependencies and integrations

The complete graph in this directory is:

```text
ServerDirectory ------> Queue
DiscordBridge --------> Queue (optional)
luckperms plugin -----> LuckPermsBridge
```

`FeatureDefinitions.java` declares all three relationship types. The corresponding feature classes and real Velocity bootstrap are included in this directory.

The `luckperms` dependency is intentionally external. `requiresPlugins(...)` is for a separately installed Velocity plugin; it would defeat the example to replace that dependency with a fake local class.

Use a capability when a consumer wants an interface/behavior rather than the lifecycle identity of one named feature.
