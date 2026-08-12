# 01 — Simple Paper feature

This is the smallest useful FeatureFramework application shape.

Files:

- `WelcomeFeature.java` — one managed feature.
- `MyPlugin.java` — a thin Paper bootstrap that creates and starts the host.

The important boundary is that `MyPlugin` does not initialize feature behavior itself. It only composes and hosts it.

Once this is clear, continue to [owned resources](../02-owned-resources/README.md).
