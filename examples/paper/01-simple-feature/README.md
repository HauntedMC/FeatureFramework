# 01 — Simple Paper feature

This is the smallest useful Paper setup: one feature, one definition, and one host.

- `WelcomeFeature.java` contains the feature behavior.
- `MyPlugin.java` creates the definition and starts/stops `PaperFeatureHost`.

The bootstrap does not initialize feature behavior itself. It only composes and hosts it.

Next: [feature-owned resources](../02-owned-resources/README.md).
