# Lifecycle

FeatureFramework separates **feature graph lifecycle**, **resource ownership**, and **native platform scheduling**.
They are related, but deliberately not the same abstraction.

## Feature lifecycle

A host owns the feature graph. Lifecycle operations are serialized by the shared lifecycle coordinator and execute
through the platform's `FeatureOperationExecutor` before the graph lock is acquired.

- **Paper:** lifecycle callbacks execute on Bukkit's primary thread. An off-thread caller is marshalled to the primary
  thread and waits for the synchronous lifecycle result. A primary-thread caller executes directly.
- **Velocity:** lifecycle callbacks execute directly on the caller. FeatureFramework does not invent a Velocity main
  thread and does not add a scheduler hop.

This ordering is intentional: a worker must never hold the graph lock while waiting for the Paper primary thread.

## Resource scopes

Each feature instance owns a resource scope. The scope transitions `OPEN -> QUIESCING -> CLOSED` and releases resources
in a deterministic order. `StandardFeatureResourceLifecycle` builds the shared policy while Paper and Velocity resource
facades retain their native manager types. New platform assembly uses `StandardFeatureResourceLifecycle.builder()` so
listeners, tasks, commands, services, data, caches, and pre-listener cleanup are named at the call site instead of being
passed as an error-prone positional callback list. The existing `create(...)` factory remains supported for
compatibility.

The standard policy is:

1. quiesce listeners, tasks, commands, services, optional data resources, and caches;
2. run any established platform cleanup that must precede listener teardown (Paper GUI shutdown is the current case);
3. unregister listeners, cancel tasks, unregister commands/services, then clean optional data resources and caches;
4. attempt every cleanup step and rethrow the first failure with later failures suppressed.

The pre-listener hook exists to preserve platform teardown semantics; it is not a generic extension point for arbitrary
feature cleanup.

## Tasks

`FeatureTaskTracker<H>` owns registration races, one-shot completion, in-flight accounting, quiescing, cancellation,
and bounded draining for a native task-handle type `H`. Platform task managers supply only native scheduling and
cancellation operations:

- Paper uses Bukkit scheduler primitives and Bukkit tick semantics;
- Velocity uses Velocity's scheduler and `Duration` semantics.

Do not add a wrapper or universal scheduler API merely to make the method names match. New shared lifecycle abstractions
should centralize behavior that is not already owned by `FeatureTaskTracker` or `StandardFeatureResourceLifecycle`.

## Reload contract

A graph or feature reload must leave stable capability references valid while advancing provider generation, fully
release resources from the previous feature instance, and re-create the new feature scope before publication.

Acceptance tests are the executable contract for these guarantees.
