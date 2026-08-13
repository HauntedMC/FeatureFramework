# Migrating an Existing Plugin

Do not rewrite a large plugin into FeatureFramework all at once. Move one clear subsystem at a time.

For framework-version compatibility changes, also see [MIGRATION.md](../MIGRATION.md).

## 1. Find the ownership boundaries

For each existing subsystem, identify its listeners, commands, tasks, caches, data resources, integrations, public APIs, and shutdown behavior. This usually makes the first feature boundaries obvious.

## 2. Start with an isolated feature

Choose something with few dependencies, such as join messages, maintenance mode, a utility system, or an external integration.

Add `@FeatureDeclaration` to its concrete feature class, move its registrations into `initialize()`, and use
feature-owned resource managers where possible. The generated catalog will include it automatically.

## 3. Test cleanup before moving on

Enable, use, disable/reload, and re-enable the feature. Check for duplicate listeners or commands, orphan tasks, stale services, and retained state.

A migration has not gained much if the new feature class still registers everything globally and relies on plugin-wide cleanup.

## 4. Replace direct subsystem access

For each cross-feature relationship, choose the right mechanism:

- named lifecycle dependency -> `requiresFeatures`;
- reusable contract -> capability;
- private application contract -> internal service;
- external platform plugin -> `requiresPlugins`.

Avoid replacing the old global manager with a new global `Services` singleton.

## 5. Move config and messages with the feature

Keep only genuinely global host settings outside features. Feature-specific defaults and localization should move with the feature that interprets them.

## 6. Migrate central systems last

Once the simpler features have proven the pattern, move shared/stateful systems and convert their consumers to explicit capabilities or services.

A sensible order is:

1. isolated feature or integration;
2. event-driven feature;
3. scheduled/background feature;
4. feature with config/messages;
5. capability provider and its consumers;
6. central stateful systems;
7. remaining bootstrap wiring.

The migration is complete when the bootstrap mostly composes features and each migrated feature can survive a full disable/re-enable cycle cleanly.
