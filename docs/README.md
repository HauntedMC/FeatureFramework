# FeatureFramework documentation

If FeatureFramework is new to you, read the [feature mental model](concepts/FEATURE-MENTAL-MODEL.md), then follow the examples for [Paper](../examples/paper/README.md) or [Velocity](../examples/velocity/README.md). The rest of this directory is reference material you can open when you need it.

## Core concepts

- [Feature mental model](concepts/FEATURE-MENTAL-MODEL.md) — features, definitions, contexts, hosts, and sensible feature boundaries.
- [Lifecycle and resources](concepts/LIFECYCLE-AND-RESOURCES.md) — ownership, cleanup, reloads, and failure handling.
- [Dependencies, capabilities, and services](concepts/DEPENDENCIES-CAPABILITIES-SERVICES.md) — how features depend on and expose functionality to each other.
- [Multi-feature plugin design](concepts/MULTI-FEATURE-PLUGIN-DESIGN.md) — organizing a larger Paper or Velocity plugin.

## Common framework areas

- [Commands and listeners](toolkits/COMMANDS-AND-LISTENERS.md)
- [Scheduling and async work](toolkits/SCHEDULING-AND-ASYNC-WORK.md)
- [Data and caching](toolkits/DATA-AND-CACHING.md)
- [Paper UI and platform adapters](toolkits/PAPER-UI-AND-PLATFORM-ADAPTERS.md)
- [Text, formatting, and safe player input](toolkits/TEXT-AND-FORMATTING.md)
- [Configuration and localization](guides/CONFIGURATION-AND-LOCALIZATION.md)
- [Programmatic message themes](guides/THEMES.md)

## Guides and reference

- [Migrating an existing plugin](guides/MIGRATING-AN-EXISTING-PLUGIN.md)
- [Testing, debugging, and operations](guides/TESTING-DEBUGGING-OPERATIONS.md)
- [Operating a large feature plugin](guides/OPERATING-A-LARGE-FEATURE-PLUGIN.md)
- [Toolkit and component index](reference/TOOLKIT-INDEX.md)
- [Feature design checklist](reference/FEATURE-DESIGN-CHECKLIST.md)

## Deeper technical documentation

- [Architecture](ARCHITECTURE.md)
- [Threading](THREADING.md)
- [Version migration notes](MIGRATION.md)
- [Coordinated release process](RELEASE.md)

## Examples

- [All examples](../examples/README.md)
- [Paper](../examples/paper/README.md)
- [Velocity](../examples/velocity/README.md)
