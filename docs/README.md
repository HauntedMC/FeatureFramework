# FeatureFramework documentation

Use this page as the documentation map. The root README is deliberately short; details live here and in the examples.

## Start here

1. [The feature mental model](concepts/FEATURE-MENTAL-MODEL.md)
2. [Lifecycle and resource ownership](concepts/LIFECYCLE-AND-RESOURCES.md)
3. Pick a platform: [Paper examples](../examples/paper/README.md) or [Velocity examples](../examples/velocity/README.md)
4. [Multi-feature plugin design](concepts/MULTI-FEATURE-PLUGIN-DESIGN.md)

## Concepts

- [Feature mental model](concepts/FEATURE-MENTAL-MODEL.md) — host, definitions, contexts, resources, and runtime responsibilities.
- [Lifecycle and resource ownership](concepts/LIFECYCLE-AND-RESOURCES.md) — how cleanup works and what a feature should own.
- [Dependencies, capabilities, and services](concepts/DEPENDENCIES-CAPABILITIES-SERVICES.md) — choose the right relationship between features.
- [Multi-feature plugin design](concepts/MULTI-FEATURE-PLUGIN-DESIGN.md) — structure real applications without a god bootstrap.

## Guides

- [Configuration and localization](guides/CONFIGURATION-AND-LOCALIZATION.md)
- [Migrating an existing plugin](guides/MIGRATING-AN-EXISTING-PLUGIN.md)
- [Testing, debugging, and operations](guides/TESTING-DEBUGGING-OPERATIONS.md)

## Reference

- [Toolkit and component index](reference/TOOLKIT-INDEX.md)
- [Feature design checklist](reference/FEATURE-DESIGN-CHECKLIST.md)

## Existing technical reference

- [Architecture](ARCHITECTURE.md) — implementation architecture and module boundaries.
- [Threading](THREADING.md) — exact Paper and Velocity execution guarantees.
- [Migration](MIGRATION.md) — compatibility/migration details for framework consumers.

## Examples

The example library is intentionally progressive. Do not begin with the largest sample.

- [All examples](../examples/README.md)
- [Paper (Bukkit-side)](../examples/paper/README.md)
- [Velocity](../examples/velocity/README.md)

Each platform starts with one feature, then introduces resource ownership, configuration, dependencies, services/capabilities, and finally full multi-feature composition.
