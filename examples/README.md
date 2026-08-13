# FeatureFramework examples

The examples are split by platform and increase in complexity:

- [Paper](paper/README.md)
- [Velocity](velocity/README.md)

## How to use these examples

**Every example directory is independent.** It contains every application class, feature class, capability contract, and helper that its source code references. You do not need to copy classes from an earlier example.

Framework/platform classes still come from the real dependencies. Add FeatureFramework to a normal Paper or Velocity project using the setup in the [root README](../README.md), then copy the example that is closest to what you are building.

The examples deliberately keep build files and platform metadata out of each directory so the same source can be dropped into an existing plugin project without duplicating Maven boilerplate dozens of times.

## Progression

Both platform tracks cover:

1. one managed feature and host bootstrap;
2. feature-owned listeners and tasks;
3. configuration and messages;
4. required/optional features and external plugins;
5. capability provider/consumer design;
6. a larger multi-feature application;
7. lifecycle, async work, and recreation;
8. DataProvider with feature-owned data resources;
9. DataRegistry and player identity readiness.

Examples 1–7 use the ready-to-use host. Examples 8–9 intentionally use the lower-level host composition because DataProvider and DataRegistry are optional integrations that change the feature resource/context type.
