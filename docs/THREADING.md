# Threading and execution guarantees

FeatureFramework shares lifecycle policy across Paper and Velocity without pretending that the two platforms have the same threading model.

## Host lifecycle operations

All graph-mutating host operations are serialized by the shared `LifecycleCoordinator`:

- start and stop;
- feature enable and disable;
- feature reload and graph reload;
- soft reload;
- direct feature loading.

The coordinator enters the configured platform `FeatureOperationExecutor` **before** acquiring the graph-operation lock. This ordering prevents an asynchronous caller from holding the lifecycle lock while waiting for a platform thread.

## Paper

Paper compositions bind `PaperFeatureOperationExecutor`. Synchronous lifecycle work therefore runs on Bukkit's primary server thread. Calls made from that thread execute immediately; calls made from another thread are marshalled to the primary thread and the caller receives the operation's return value or failure synchronously.

This guarantee covers feature construction/preparation, `initialize()`, configuration application, `disable()`, framework resource cleanup, service activation/deactivation and graph mutation hooks.

Paper's `FeatureCommandManager` uses the same execution primitive. Command registration and unregistration therefore have synchronous completion semantics even when invoked by an asynchronous caller.

Feature code should still use `FeatureTaskManager` async methods for blocking or computational work. Bukkit/Paper API access performed by the feature remains subject to Paper's normal thread-safety rules.

## Velocity

Velocity uses the shared direct executor. Feature lifecycle operations execute on the calling thread while the lifecycle coordinator serializes graph mutations. FeatureFramework does not introduce an artificial proxy "main thread" or an unnecessary scheduler hop.

Velocity task execution continues to use Velocity's native scheduler.

## Shared task ownership

Paper and Velocity task managers use the shared `FeatureTaskTracker` for:

- OPEN / QUIESCING / CLOSED state;
- rejecting registration after quiescing starts;
- one-shot completion races;
- active-handle tracking;
- in-flight callback tracking;
- cancellation failure aggregation;
- bounded shutdown draining.

The platform adapters remain responsible only for native scheduling, timing units and handle cancellation.

## Platform boundary

Code in `featureframework-core` must not import Bukkit, Paper or Velocity APIs. Platform modules translate shared policy into their native APIs. Optional facilities such as Paper inventories, PlaceholderAPI, DataProvider, and DataRegistry remain in dedicated toolkit or integration artifacts.
