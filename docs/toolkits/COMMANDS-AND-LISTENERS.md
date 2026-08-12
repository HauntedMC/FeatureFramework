# Commands and Listeners

Commands and listeners are **ingress**: they allow the platform to call into a feature. That makes ownership especially important during reloads and shutdown.

## Ownership rule

If a command or listener exists because one feature is enabled, register it through that feature's resource manager when FeatureFramework provides an adapter.

Paper exposes feature-owned command and listener managers through `PaperFeatureResources`. Velocity exposes the equivalent proxy managers through `VelocityFeatureResources`.

When cleanup begins, ingress is quiesced before feature domain state is released. This prevents new callbacks from entering a feature while it is shutting down.

## Listeners

Paper's `FeatureListenerManager` supports both normal `Listener` registration and programmatic event registration. The manager tracks registrations and unregisters them with the feature.

Prefer feature-local listener classes:

```text
ChatFeature
├── ChatService
├── ChatListener
└── ChatCommand
```

Avoid a single plugin-global listener that dispatches every event to unrelated features; it obscures ownership and creates manual enable/disable checks everywhere.

Velocity follows the same ownership principle with its native listener adapter.

## Commands

FeatureFramework platform modules provide feature-owned command managers and Brigadier integration. Commands registered through the feature scope can be withdrawn when the feature stops, avoiding duplicate registrations after recreation.

Keep command code thin:

```text
command input
  -> validate platform/user concerns
  -> call feature/domain service
  -> render localized result
```

Do not put the feature's complete business logic in a command executor.

## When direct registration is acceptable

Use the native platform API directly when the framework does not expose the required integration. In that case, treat registration handles as domain-owned resources and explicitly unregister them during feature shutdown.

## Review questions

- Which feature owns this callback?
- Can callbacks arrive after shutdown starts?
- Will recreation register a duplicate?
- Does the command depend on another feature contract that should be declared?
- Is user-facing output localized rather than embedded in the handler?
