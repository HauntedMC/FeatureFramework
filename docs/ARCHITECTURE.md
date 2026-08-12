# Architecture

## Goals

FeatureFramework supplies feature-independent mechanics while application plugins supply feature
catalogs, domain APIs, persistence entities, commands, and concrete behavior. The framework follows
dependency inversion: shared code defines small contracts and platform modules adapt them to Paper or
Velocity.

The 1.2 architecture deliberately maximizes shared implementation without pretending Paper and
Velocity have the same execution model. Common ownership, graph, scope, and composition mechanics live
once in `shared`; native platform objects and thread requirements remain in their adapters.

## Dependency direction

```text
featureframework-api
        ^
        |
featureframework-shared
        ^          ^
        |          |
featureframework-paper   featureframework-velocity
        ^                         ^
        |                         |
Paper application        Velocity application
```

Dependency-free integration contracts belong in `api`; cross-platform implementation belongs in
`shared`. A class belongs in a platform module when its contract or implementation requires that
platform. Paper and Velocity modules must not import each other. Source-boundary tests enforce these
rules and also prevent the shared host façade from reabsorbing low-level graph mechanics.

FeatureFramework owns mechanics, not business vocabulary. Economy, admission, queue, sanctions,
presence, and similar capability contracts remain in their product/domain API even when more than one
plugin consumes them. Product API roots extend `FeatureFrameworkApi<V>` and add only their version
type and domain capabilities.

## Host responsibilities

`FeatureHost` is the public platform-neutral orchestration façade. It owns operation serialization,
runtime state transitions, host-level reload hooks, and the public framework API. Its implementation is
split into two package-private collaborators:

- `FeatureInventory` owns discovery, the available/loaded registry view, key resolution, load ordering,
  dependency diagnostics, plugin-dependency checks, and public catalog registration.
- `FeatureInstanceController` owns live feature preparation, dependency-aware construction and
  activation, snapshot-backed reload transactions, and instance shutdown/removal.

Keeping those collaborators package-private avoids adding public API surface merely to improve internal
maintainability. `FeatureHostComposition` owns the platform-neutral composition algorithm used by both
platform façades: scope factories, context assembly, host callbacks, reload wiring, and scope clearing.
`PaperFeatureHostComposition` and `VelocityFeatureHostComposition` only provide native platform
factories and hooks.

## Lifecycle and execution model

Features implement the neutral `Feature` contract. The construction descriptor
`nl.hauntedmc.featureframework.loader.FeatureDescriptor<F, C>` contains the implementation class,
constructor, and dependency declarations needed by the host. It is distinct from
`nl.hauntedmc.featureframework.api.feature.FeatureDescriptor`, which is implementation-free metadata
published through the public catalog.

`ManagedFeatureContext`, `PaperFeatureContext`, and `VelocityFeatureContext` carry descriptor,
configuration, localization, resource, capability, and internal-service state, so consumers do not
define parallel context records.

Resources move through `OPEN`, `QUIESCING`, and `CLOSED`. Shared ownership infrastructure contains the
state machines that do not depend on native platform types:

- `FeatureTaskTracker<H>` owns task registration races, in-flight accounting, quiescing, cancellation,
  and bounded draining while adapters supply native scheduling/cancellation callbacks.
- `FeatureRegistrationTracker<T>` owns listener-registration state and cleanup bookkeeping.
- `StandardFeatureResourceLifecycle` owns the standard quiesce/cleanup ordering.
- `FeatureResourceFactoryCore` creates common service, cache, and data-resource pieces.

Platform task/listener/command managers retain only native scheduling, registration, command dispatch,
and handle types. Paper GUI cleanup is an additional Paper-only resource action. New abstractions are
introduced only when they centralize behavior that these shared state machines do not already own; a
pass-through wrapper is not considered a maintainability improvement.

`LifecycleCoordinator` serializes host graph mutations, but serialization is intentionally separate
from execution affinity. `FeatureOperationExecutor` is entered before the graph lock is acquired.
Paper binds `PaperFeatureOperationExecutor`: a lifecycle call made off the Bukkit primary thread is
scheduled onto that thread and the caller synchronously waits for completion. A call already on the
primary thread executes directly. Velocity retains the shared direct executor, so it does not pay for
an invented main-thread hop. Native task scheduling remains asynchronous/synchronous according to the
platform-specific task API. See `THREADING.md` for the detailed contract and deadlock rule.

`LifecycleFeature` gives Paper and Velocity feature bases one shutdown policy: quiesce ingress,
withdraw services, run feature cleanup, then release framework resources. Loading uses shared key
resolution, dependency diagnostics/traversal, topological ordering, dependent closure, and ordered
graph start/stop helpers. `FeatureStartupCoordinator` owns construction-through-activation rollback
semantics and `FeatureOperationCoordinator` owns administrative enable/disable/reload cascades.

Runtime-only collaboration uses `InternalServiceRegistry<O>`. `FeatureServiceManager<O>` applies the
same staged activation and cleanup policy to public and internal registries through the
`OwnedServiceRegistry<O>` port. Ownership is application-defined, replacement is restricted to the
current owner, registration handles are idempotent, and stale handles cannot remove a replacement
provider.

## Configuration, localization, and logging

YAML writes are copy-on-write and use atomic replacement where the filesystem supports it. Paths are
normalized under the configured data directory. `FeatureConfigHandler` owns default merging, schema
mismatch policy, and reload listeners. `FeatureConfigurationRoot` owns the global feature enablement
map. `LocalizationStore` owns bundled defaults, language files, feature-to-framework fallback, and
reload behavior. `ComponentLocalization` adds fluent component rendering, player-language selection,
platform placeholder hooks, and static-message caching; consumer adapters only provide the player
type and language/placeholder callbacks. `FrameworkLogger` is the logging boundary; feature-prefixed
adapters support JUL (Paper) and SLF4J/Adventure (Velocity). The Velocity module also owns reusable
structured connection-event logging.

Administrative command front ends remain platform bindings, while `FeatureCommandModel`,
`FeatureCommandView`, and `FeatureOperationMessages` own platform-neutral lookup, suggestions,
rendering, and operation-result mapping. Existing public package names are kept stable in 1.2 even when
a different package might look cleaner; cosmetic public moves are deferred to a major release rather
than represented by duplicate public facade classes.

## Consumer boundary

`FeatureDefinition` removes product-specific manifest record boilerplate and `FeatureCollection` lets a
plugin compose one or more feature packs into one artifact. Consumers should use `PaperFeatureHost`,
`VelocityFeatureHost`, or the corresponding composition type rather than owning a parallel graph or
scope implementation.

Consumer-side classes are permitted only as application composition or concrete behavior: plugin
bootstrap and metadata, feature collections, domain capability contracts/adapters, persistence
integrations, operator commands, and concrete feature code. Reusable algorithms and resource assembly
must not be reimplemented there.

Applications retain no framework feature base or framework `host` package. Their concrete features
extend the corresponding FeatureFramework platform base directly, while bootstrap composition selects
optional DataProvider/DataRegistry integration. Compatibility accessors, DataRegistry gate plumbing,
player-reference resolution, proxy access, feature contexts, configuration/localization handlers,
resource managers, graph loaders, construction descriptors, registries, and capability implementations
are framework-owned.

## Compatibility and packaging

Platform dependencies use `provided` scope. Consumers shade framework artifacts but do not embed the
Paper or Velocity API. Public framework packages use `nl.hauntedmc.featureframework`; consumer domain
APIs retain their own namespaces. Releases publish normal, source, and Javadoc artifacts.

The codebase distinguishes consumer API, framework-only cross-artifact SPI, and internal
implementation. A new SPI namespace is used only when Java visibility across Maven artifacts genuinely
requires it; existing public infrastructure is not wrapped merely to reclassify it. Internal refactors
prefer package-private or `internal.*` implementation types where that does not move an existing public
FQCN.

The `API Compatibility` workflow builds the tagged `v1.0.0` baseline and the current branch, then
compares every published framework module's public classes. Binary or source incompatible public API
changes fail CI. Internal refactors should therefore preserve existing public façades unless a
deliberate major-version change is planned.

## Acceptance boundary

The `platform-acceptance` Maven profile packages independent dummy Paper and Velocity plugins. Each
plugin hosts a two-feature collection: a provider publishes a public capability and a consumer declares
and resolves it. The fixtures exercise a real repeating task, listener, Brigadier command, and service,
then reload the provider graph and assert the retired resource scope is `CLOSED` with no owned handles
remaining. They repeat the cleanup assertions on final host shutdown.

Paper additionally initiates reload from an asynchronous scheduler thread and asserts feature
initialize/disable callbacks execute on Bukkit's primary thread. Velocity records the scheduler caller
thread and asserts provider/consumer lifecycle callbacks remain on that same thread, proving the direct
executor adds no synthetic lifecycle hop. Both fixtures verify dependent recreation, stable
capability-reference generation changes, public catalog transitions, and clean host shutdown against
pinned real platform runtimes.
