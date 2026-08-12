# Configuration and Localization

Configuration and messages are feature-scoped parts of the managed feature contract.

## Defaults belong to the feature

`ManagedFeature` supplies empty defaults. Override them only when your feature needs them:

```java
@Override
public ConfigMap getDefaultConfig() {
    ConfigMap config = new ConfigMap();
    // Add the feature's defaults here using ConfigMap's typed helpers.
    return config;
}

@Override
public MessageMap getDefaultMessages() {
    MessageMap messages = new MessageMap();
    // Add the feature's message defaults here.
    return messages;
}
```

Keeping defaults with the implementation has two advantages: the feature is self-describing, and moving the feature to another host does not require copying an unrelated global config block.

## Read through the feature configuration handler

The context exposes `configHandler()` / `getConfigHandler()`. Treat it as the source for the feature's effective configuration rather than reloading YAML directly from arbitrary code paths.

## Reload behavior

A configuration change does not automatically mean every feature can mutate safely in place.

Use `applyConfiguration()` to signal the supported behavior. The default managed implementation returns `RECREATE_REQUIRED`, which is a safe choice for stateful features.

Soft application is appropriate when:

- the change only adjusts thresholds, messages, or other replaceable values;
- no listener/command topology must change unsafely;
- the operation can be made atomic from callers' perspective.

Prefer recreation when:

- dependencies change;
- expensive resources need rebuilding;
- callbacks capture old state;
- correctness is easier to guarantee through a fresh feature instance.

## Localization

Paper and Velocity provide platform localization adapters in their feature contexts. Keep user-facing messages in the feature message map/localization system rather than embedding formatted strings throughout listeners and commands.

Recommended pattern:

```text
Feature implementation
  -> domain result
  -> presentation/command/listener
  -> feature localization key
  -> rendered message
```

This keeps domain logic independent of language and formatting.

## Configuration design rules

- use stable, descriptive keys;
- provide defaults for every optional setting;
- validate required values during initialization;
- fail early on configuration that would create unsafe state;
- do not let multiple features silently own the same key;
- document behavior, units, ranges, and reload semantics;
- keep secrets out of committed example files.

## Example progression

Both platform example suites include a config/messages example before introducing cross-feature composition. That is intentional: understand the lifecycle boundary first, then add dependencies.
