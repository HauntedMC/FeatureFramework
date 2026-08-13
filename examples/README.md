# FeatureFramework examples

The examples are arranged from small to advanced and are split by platform:

- [Paper](paper/README.md)
- [Velocity](velocity/README.md)

Each directory focuses on one FeatureFramework concept rather than being a complete standalone Maven plugin. Normal application/domain classes that do not add anything to that lesson may be omitted.

Both tracks cover the same progression:

1. one feature and host bootstrap;
2. owned listeners/tasks/resources;
3. config and messages;
4. feature and plugin dependencies;
5. capability provider/consumer;
6. larger multi-feature composition;
7. reload, async, and lifecycle decisions.

Start at the level closest to what you are building. The Java examples use the current FeatureFramework APIs; the surrounding plugin metadata and build setup are intentionally left to the consuming application.
