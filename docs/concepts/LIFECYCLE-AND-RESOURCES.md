# Lifecycle and Resource Ownership

Lifecycle ownership is the core reliability feature of FeatureFramework.

A feature should be able to stop without leaving ingress or work behind.

## Startup

During `initialize()`, a feature creates domain objects and registers platform/framework resources through its scoped context.

Typical owned resources include:

- listeners;
- commands;
- scheduled tasks;
- caches;
- GUIs and other platform adapters;
- data resources;
- published feature services.

Registration through the feature scope matters because the framework can then track what must be quiesced and released.

## Shutdown order

Managed features use a deliberate cleanup sequence:

1. configuration reload listeners are detached;
2. platform-specific pre-quiesce hooks may run;
3. resource ingress is quiesced;
4. callable services are deactivated;
5. the feature's `disable()` hook runs;
6. tracked resources are cleaned up.

This prevents a common plugin-reload race: a listener, command, task, or service calls into state while that state is already being destroyed.

## What belongs in `disable()`?

Release **domain state that the framework does not already own**.

Good examples:

- close a custom client created directly by the feature;
- flush an in-memory aggregate if that is your feature's responsibility;
- detach from a third-party callback registration not wrapped by a framework tracker;
- clear your own domain collections.

Do not manually unregister every framework-owned listener/task/command/service a second time. The lifecycle scope owns those.

## Resource-manager rule

Prefer:

```java
resources().getListenerManager().registerListener(listener);
resources().getTaskManager().scheduleRepeatingTask(task, period);
```

over registering directly with global platform managers when FeatureFramework provides an owned adapter.

The direct platform API is still available, but direct registrations become your manual cleanup responsibility.

## Reloads

There are two useful mental models:

- **soft configuration application** — the feature remains alive and applies compatible configuration changes;
- **recreation/graph reload** — affected feature instances stop and are recreated in dependency-safe order.

Return the appropriate `ConfigReloadResult` from `applyConfiguration()` for your feature. Prefer recreation when state cannot safely be mutated in place.

## Failure behavior

Treat `initialize()` as a transaction boundary. If initialization fails, do not assume partially registered resources can remain. The host/lifecycle machinery is designed around rollback and cleanup of managed resources.

Feature initialization should therefore:

- fail fast on invalid required state;
- register resources through owned managers;
- avoid launching untracked background work;
- avoid publishing a service before its backing state is ready.

## Paper vs Velocity

Paper lifecycle operations obey the primary-thread execution contract documented in [Threading](../THREADING.md). Velocity host lifecycle operations execute directly on the caller. Scheduling remains platform-native through each platform's adapters.

## Operational payoff

When ownership is consistent, administrators can disable/reload one feature without treating the entire plugin JVM state as disposable. Developers also get a precise answer to “who owns this listener/task/service?”: the feature that registered it.
