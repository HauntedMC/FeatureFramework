# Commands and Listeners

Commands and listeners let the platform call into a feature, so they should normally have the same lifetime as that feature.

## Listeners

Register listeners through the feature listener manager when possible. Paper's `FeatureListenerManager` supports normal Bukkit listeners and programmatic event registration; Velocity has an equivalent owned listener manager.

```java
resources().listeners().registerListener(new JoinListener());
```

The framework unregisters managed listeners when the feature stops. This prevents duplicate registrations after recreation and avoids callback code reaching state that has already been torn down.

Keep listeners close to the feature that owns their behavior. A plugin-wide listener that manually dispatches every event to unrelated systems usually makes ownership harder to follow.

## Commands

Paper and Velocity both provide feature-owned command managers and Brigadier adapters. Registering a command through the feature resource scope lets the framework remove it with the feature.

Keep command handlers small: parse and validate input, call domain code, then render the result. The command class does not need to contain the whole feature implementation.

## When to use the native platform API

Use the Paper or Velocity API directly when FeatureFramework does not wrap the integration you need. If that registration survives the current method call, keep its unregister/close handle and release it during feature shutdown.

See [Paper owned resources](../../examples/paper/02-owned-resources/README.md) and [Velocity owned resources](../../examples/velocity/02-owned-resources/README.md).
