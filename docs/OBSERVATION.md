# Lifecycle operation observation

FeatureFramework exposes an optional, vendor-neutral observation SPI for measuring or tracing meaningful host and feature lifecycle operations without depending on OpenTelemetry, HauntedObservability, or another telemetry implementation.

## Attach an observer

Paper and Velocity hosts accept an observer during construction:

```java
PaperFeatureHost.builder(plugin, ApiRoot.class, features)
        .observer(observer)
        .build();
```

```java
VelocityFeatureHost.builder(plugin, proxy, logger, dataDirectory, ApiRoot.class, features)
        .observer(observer)
        .build();
```

The observer belongs to that host instance. There is no static observer registry, service locator, or global registration. Existing builders remain source-compatible because the default is `FeatureFrameworkObserver.noop()`.

## Public contract

The dependency-free API consists of:

- `FeatureFrameworkObserver`, which starts one observation;
- `FeatureFrameworkObservation`, which optionally activates adapter-specific context and receives terminal completion;
- `FeatureFrameworkObservationScope`, which propagates adapter context while FeatureFramework executes the operation;
- `FeatureFrameworkOperationContext`, which contains only the bounded operation kind and optional framework-owned `FeatureId`;
- `FeatureFrameworkOperationKind`, the stable operation vocabulary;
- `FeatureFrameworkOperationOutcome`, a bounded terminal classification.

Runtime exceptions from observer start, scope activation, completion, and scope cleanup are isolated from FeatureFramework behavior. Java `Error`s are not swallowed. An observability adapter must be non-blocking; FeatureFramework never requires one to be present.

With the default no-op observer, FeatureFramework executes lifecycle work without constructing an observation context or running terminal observation classification. A custom observer necessarily receives the bounded context so it can decide whether to observe an operation; if it filters that operation by returning `FeatureFrameworkObservation.noop()`, FeatureFramework then skips scope activation, terminal classification, and completion for that operation.

## Operation vocabulary

The initial contract observes:

- `HOST_START`
- `HOST_STOP`
- `FEATURE_LOAD`
- `FEATURE_ENABLE`
- `FEATURE_DISABLE`
- `FEATURE_RECREATE`
- `FEATURE_SOFT_RELOAD`
- `GRAPH_RELOAD`
- `FILE_RESET`

`FEATURE_LOAD` is emitted from the one actual startup path in `FeatureInstanceController`. It therefore covers initial startup, explicit enable, dependency-driven startup, recreation, graph reload, and reset-driven restart. Higher-level operations are emitted only at their public serialized host boundary, so internal reload recursion does not create duplicate `FEATURE_ENABLE`/`FEATURE_DISABLE` operations.

A normal recreation can therefore look like:

```text
FEATURE_RECREATE lottery
└── FEATURE_LOAD lottery
```

A graph reload can contain multiple nested `FEATURE_LOAD` operations without pretending that each internal reconciliation step was a separately requested enable/recreate command.

## Outcomes

The stable outcomes are:

- `SUCCESS` — requested lifecycle work completed;
- `NO_CHANGE` — the requested state already held, such as disabling an already-unloaded feature;
- `SKIPPED` — a bounded precondition prevented work, such as a missing feature/dependency or unavailable reset target;
- `FAILURE` — lifecycle work was attempted and failed.

Where FeatureFramework owns a concrete `Throwable`, it is supplied separately as diagnostic context. Exception messages must not be turned into metric labels.

## Metadata and cardinality boundary

`FeatureFrameworkOperationContext` contains exactly:

- a `FeatureFrameworkOperationKind`; and
- a `FeatureId` only when the operation is feature-scoped.

It deliberately does **not** expose configuration values, file paths, plugin objects, dependency lists, command arguments, player identifiers, server addresses, database information, SQL/query text, arbitrary caller strings, or arbitrary attribute maps.

The operation kind and FeatureId are the intended bounded dimensions for metrics. Adapters may attach richer failure detail to traces/logs according to their own privacy policy, but not as metric labels.

## Layering with DataRegistry and DataProvider

The three neutral SPIs represent different ownership layers:

```text
FeatureFramework lifecycle operation
├── DataRegistry semantic/domain operation
└── DataProvider storage operation
```

FeatureFramework answers **which feature or host lifecycle operation is running**. DataRegistry answers **which registry/domain operation is running**. DataProvider answers **which backend/storage operation is running**. HauntedObservability should preserve this hierarchy rather than duplicating storage or registry instrumentation inside FeatureFramework.

FeatureFramework does not register DataRegistry or DataProvider observers itself. The application composition root attaches all three observers to their respective runtimes.

## Scope boundary

This release intentionally does not observe every configuration read, dependency check, repository call, resource cleanup callback, event/listener invocation, or preview operation. `previewFileReset(...)` remains a read-only operation and is not observed.

FeatureFramework itself has no OpenTelemetry or HauntedObservability dependency. The later HauntedObservability FeatureFramework integration will implement this neutral SPI.
