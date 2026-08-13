# Lifecycle and Resources

A managed feature should be able to stop cleanly without leaving listeners, commands, tasks, services, or other resources behind.

## Register resources through the feature

During `initialize()`, create the feature's domain objects and register framework/platform resources through its scoped resource managers whenever an adapter exists.

Common owned resources include:

- listeners and commands;
- scheduled tasks;
- caches;
- Paper GUIs;
- data resources;
- published services.

For example:

```java
resources().getListenerManager().registerListener(listener);
resources().getTaskManager().scheduleRepeatingTask(task, period);
```

The direct Paper or Velocity APIs are still available. If you register something directly, however, its cleanup is your responsibility.

## What happens during shutdown

FeatureFramework stops new framework-managed callbacks and services before feature state is released, then cleans up the tracked resources. This avoids callbacks arriving while `disable()` is tearing down the state they use.

You normally do **not** need to manually unregister framework-owned listeners, commands, tasks, or services in `disable()`.

Use `disable()` for state the framework does not own, for example:

- a third-party client created directly by the feature;
- an external subscription with its own close handle;
- feature-specific in-memory state that needs flushing or clearing.

## Reloading configuration

`applyConfiguration()` tells the host whether a feature can safely use new configuration without being recreated. The managed default is `RECREATE_REQUIRED`.

Use a live/soft configuration update only when the affected state can be changed consistently. Recreation is usually safer when callbacks, clients, dependency relationships, or long-lived state depend on the old configuration.

## Initialization failures

Treat `initialize()` as a startup boundary: validate required state early and let startup fail if the feature cannot work correctly.

A few practical rules help rollback remain predictable:

- register resources through owned managers;
- do not start background work you cannot cancel;
- do not expose a service until the state behind it is ready;
- keep required dependencies explicit in `@FeatureDeclaration`.

## Paper and Velocity differ

Paper host lifecycle operations follow Bukkit primary-thread rules. Velocity lifecycle operations execute directly on the caller. Scheduling remains platform-specific on both platforms.

Read [Threading](../THREADING.md) before mixing lifecycle changes with asynchronous database, HTTP, or network work.
