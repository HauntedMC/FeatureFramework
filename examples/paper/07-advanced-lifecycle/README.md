# 07 — Advanced Paper lifecycle

This example deliberately owns two different kinds of resource:

1. the repeating refresh task is registered through `FeatureTaskManager`, so FeatureFramework cancels it automatically;
2. `ExampleRemoteClient` is created directly by the feature, so the feature closes it in `disable()`.

`RemoteSyncFeature` returns `RECREATE_REQUIRED` because the refresh schedule is built from configuration during initialization. Recreating the feature gives the new instance a fresh task and client instead of mutating an already-running schedule.

```text
initialize()
  -> create manually owned client
  -> register framework-owned task

cleanup
  -> framework quiesces callbacks/tasks
  -> disable() closes manual client
  -> framework releases tracked resources
```

This distinction—**framework-owned vs feature-owned directly**—is the key advanced lifecycle rule.
