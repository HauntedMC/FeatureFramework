# Scheduling and Async Work

Scheduled and asynchronous work must follow both **feature lifetime** and **platform threading rules**.

## Feature-owned tasks

Paper's `FeatureTaskManager` tracks one-shot, delayed, repeating, and asynchronous Bukkit tasks. It also exposes `runAsync` and `supplyAsync` helpers backed by tracked tasks/futures.

Register recurring work through the feature task manager when it belongs to one feature. Cleanup cancels tracked work so recreation does not leave old loops running.

Velocity provides its own feature-owned task manager around Velocity-native scheduling. Do not copy Bukkit assumptions into proxy code.

## Paper thread safety

Paper host lifecycle operations are marshalled according to the framework's primary-thread contract, but an **async task remains async**. Bukkit APIs that require the primary thread still require the normal synchronization/handoff.

Example flow:

```text
feature task manager: async database read
       |
       v
pure/domain computation
       |
       v
schedule synchronous Paper task for Bukkit world/player mutation
```

Read [THREADING.md](../THREADING.md) for the authoritative contract.

## Velocity concurrency

Velocity lifecycle operations run directly on the caller. Decide deliberately which executor/caller owns lifecycle mutations and coordinate shared state normally. The framework does not invent a global proxy main thread.

## Long-running work

For external clients, subscriptions, executors, or streams not wrapped by a feature manager:

1. stop accepting new work during shutdown;
2. cancel/close outstanding work where appropriate;
3. prevent late completions from mutating a recreated feature instance;
4. release the client/executor in the feature lifecycle.

## Avoid

- untracked repeating tasks created from global schedulers;
- futures whose callbacks capture feature state indefinitely;
- blocking database/HTTP work on Paper's primary thread;
- assuming feature disable waits for arbitrary untracked work;
- scheduling loops whose cancellation handle is lost.
