# 07 — Advanced Velocity lifecycle

`NetworkSyncFeature` combines a framework-owned repeating task with a manually owned client.

The task is registered through `VelocityFeatureResources`, so it is cancelled by framework cleanup. `ExampleNetworkClient` is created directly, so `disable()` closes it.

The polling interval is read during initialization. Returning `RECREATE_REQUIRED` makes a config reload build a clean task/client pair instead of trying to alter a live schedule.

Velocity lifecycle operations execute on their caller. The framework owns resource lifetime, but it does not create a Bukkit-style main thread or remove the need to coordinate your own concurrently accessed domain state.
