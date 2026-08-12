# Migrating an Existing Plugin

Do not rewrite a large plugin into FeatureFramework in one pass. Migrate by lifecycle boundary.

For framework-version-specific compatibility details, also read [MIGRATION.md](../MIGRATION.md).

## 1. Inventory owned resources

For each current subsystem, list:

- listeners;
- commands;
- tasks;
- caches;
- database/data resources;
- external plugin integrations;
- APIs consumed by other subsystems;
- startup/shutdown assumptions.

This usually reveals natural feature boundaries immediately.

## 2. Extract one low-dependency feature

Start with functionality that has few cross-plugin dependencies: join messages, maintenance mode, simple utilities, or an isolated integration.

Create its `FeatureDefinition`, move platform registrations into its `initialize()`, and register them through feature-owned resource managers.

Do not extract your most central subsystem first.

## 3. Make cleanup real

Before migrating another feature, prove that the first one can be enabled, disabled, and recreated without:

- duplicate listeners;
- duplicate commands;
- orphan tasks;
- stale services;
- retained domain state.

A migration that only changes class names but keeps global registrations does not gain the framework's main benefit.

## 4. Replace direct cross-subsystem access

For each dependency, decide:

- specific lifecycle dependency -> `requiresFeatures`;
- reusable contract -> capability;
- private application collaboration -> internal service;
- external platform plugin -> `requiresPlugins`.

Do not build a new global `Services` singleton to avoid making this decision.

## 5. Thin the bootstrap incrementally

As features move, delete their wiring from the bootstrap. The end state should be composition, not orchestration of every subsystem.

## 6. Move config/messages with the feature

Once ownership is clear, move feature-specific defaults and localization to the feature contract. Keep only genuinely global host settings outside it.

## 7. Introduce advanced toolkit adapters last

First get lifecycle and dependency boundaries correct. Then replace direct platform registrations with FeatureFramework command/task/listener/cache/GUI/data adapters where they improve ownership and consistency.

## Suggested migration order

1. isolated utility/integration feature;
2. event-driven feature;
3. scheduled/background feature;
4. feature with config/messages;
5. capability provider;
6. capability consumers;
7. central stateful features;
8. remaining global bootstrap wiring.

## Definition of done

A migrated feature is not complete until its resource ownership and relationship declarations are explicit and its disable/reload behavior has been exercised.
