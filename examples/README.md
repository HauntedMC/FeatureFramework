# FeatureFramework examples

This directory is a progressive cookbook for application developers. The examples are intentionally **not** part of the framework Maven reactor: they teach composition patterns without becoming production modules of FeatureFramework itself.

Choose your platform:

- [Paper (Bukkit-side)](paper/README.md)
- [Velocity](velocity/README.md)

## Progression

Both tracks follow the same order:

1. one minimal feature;
2. feature-owned platform resources;
3. feature configuration and messages;
4. dependencies and optional integrations;
5. capability provider/consumer design;
6. a realistic multi-feature plugin composition;
7. advanced lifecycle/reload decisions.

The goal is not to memorize APIs. Learn the ownership model first, then copy the pattern closest to the feature you are building.

## About the Java files

Source files are focused examples. Package declarations use `com.example...`, imports may be shortened to keep the teaching point visible, and application-specific API interfaces are intentionally small. For exact overloads and platform semantics, use the framework Javadocs/source and [documentation map](../docs/README.md).
