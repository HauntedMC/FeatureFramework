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
in a deterministic order. Platform resource facades keep native manager types while the shared lifecycle core owns the
common state machine and cleanup policy.

The common policy is:

1. quiesce listeners, tasks, commands, services, optional data resources, and caches;
2. unregister or cancel owned resources;
3. run platform-specific final cleanup (for example Paper GUI shutdown);
4. aggregate cleanup failures rather than silently abandoning later cleanup steps.

## Tasks

`TaskLifecycleCore` owns registration races, one-shot completion, in-flight accounting, quiescing, cancellation, and
bounded draining. Native scheduling remains native:

- Paper uses Bukkit scheduler primitives and Bukkit tick semantics;
- Velocity uses Velocity's scheduler and `Duration` semantics.

Do not add a universal scheduler API merely to make the method names match.

## Reload contract

A graph or feature reload must leave stable capability references valid while advancing provider generation, fully
release resources from the previous feature instance, and re-create the new feature scope before publication.

Acceptance tests are the executable contract for these guarantees.
