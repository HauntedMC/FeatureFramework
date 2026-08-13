# Scheduling and Async Work

Scheduled work should be owned by the feature that created it and still follow the threading rules of the platform.

## Paper

`FeatureTaskManager` supports tracked one-shot, delayed, repeating, and asynchronous Bukkit tasks. It also provides `runAsync` and `supplyAsync` helpers.

Use the task manager for work that should disappear when the feature stops:

```java
resources().getTaskManager().scheduleRepeatingTask(
        this::refresh,
        BukkitTime.seconds(30)
);
```

An async FeatureFramework task is still async. Bukkit APIs that require the primary thread must still be called from the primary thread.

A common flow is:

```text
async database/HTTP work
        -> pure computation
        -> schedule Paper task for Bukkit state changes
```

## Velocity

Velocity has its own feature task manager using `Duration` and Velocity's scheduler. Do not copy Bukkit main-thread assumptions into proxy code; host lifecycle operations execute directly on their caller.

## Work outside the task manager

If a feature owns an external executor, subscription, client, or future chain directly, make shutdown explicit:

1. stop accepting new work;
2. cancel or close outstanding work where appropriate;
3. prevent late callbacks from mutating a recreated feature instance;
4. release the client/executor during feature cleanup.

See [Threading](../THREADING.md) for the exact platform contract.
