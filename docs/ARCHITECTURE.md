# Architecture

## Goals

FeatureFramework supplies feature-independent mechanics while application plugins supply feature
catalogs, domain APIs, persistence entities, commands, and concrete behavior. The framework follows
dependency inversion: shared code defines small contracts and platform modules adapt them to Paper or
Velocity.

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
`shared`. A class belongs in a platform module only when its public
contract mentions that platform. Concrete feature registration and the plugin bootstrap always remain
in the consumer.

FeatureFramework owns mechanics, not business vocabulary. Economy, admission, queue, sanctions,
presence, and similar capability contracts remain in their product/domain API even when more than one
plugin consumes them. Product API roots extend `FeatureFrameworkApi<V>` and add only their version
type and domain capabilities.

## Lifecycle model

Features implement the neutral `Feature` contract. Generic `FeatureDescriptor<F, C>` and
`FeatureRegistry<F, D>` types provide reflection-free construction and defensive, synchronized
runtime registration. `ManagedFeatureContext`, `PaperFeatureContext`, and `VelocityFeatureContext`
carry descriptor, configuration, localization, resource, capability, and internal-service state, so
consumers do not define parallel context records.
Resources move through `OPEN`, `QUIESCING`, and `CLOSED`. `FeatureLifecycle` aggregates failure-safe
quiesce/cleanup, while platform command, listener, and task managers own platform handles.
`LifecycleFeature` gives Paper and Velocity feature bases one shutdown policy: quiesce ingress,
withdraw services, run feature cleanup, then release framework resources. `PaperFeature` and
`VelocityFeature` add typed plugin, logger, localization, and resource access. Their
`PaperDataRegistryFeature` and `VelocityDataRegistryFeature` specializations also own scheduler,
logging, host-readiness, and player-lookup plumbing for DataRegistry identity gates. Their
`PaperDataProviderFeature` and `VelocityDataProviderFeature` specializations provide the standard
framework data-manager type used by both products. Typed contexts receive product API lookups at host
composition, so consumers do not repeat lifecycle or platform-integration mechanics. Loading
uses shared key resolution, dependency diagnostics/traversal, topological ordering, dependent closure,
and ordered graph start/stop helpers. Reload operations return typed result records.
`FeatureStartupCoordinator` owns construction-through-activation rollback semantics,
`FeatureOperationCoordinator` owns enable/disable/reload cascades, and `FeatureReloadState` prevents
platform loaders from maintaining subtly different state machines. Platform loaders are composition
roots: they supply construction, registration, platform-dependency, and logging callbacks.

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
rendering, and operation-result mapping.

`FeatureDefinition` removes product-specific manifest record boilerplate and `FeatureCollection` lets a
plugin compose one or more feature packs into one artifact. `FeatureHost` is the platform-neutral graph
owner: it performs discovery, storage preparation, configured enablement, dependency loading, catalog
transitions, staged service activation, reload/rollback, and reverse shutdown. The `PaperFeatureHost`
and `VelocityFeatureHost` adapters provide complete default composition roots. Products that need a
custom API version or DataProvider policy use `PaperFeatureHostComposition` or
`VelocityFeatureHostComposition`; those classes assemble `FeatureHost`, `FeatureScopeFactory`, typed
platform contexts, platform resource factories, reload hooks, and platform-dependency checks. No
graph, scope, lifecycle, configuration, localization, command-ownership, or service-registry
implementation remains in the product.

Consumer-side classes are permitted only as application composition or concrete behavior: plugin
bootstrap and metadata, feature collections, domain capability contracts/adapters, persistence
integrations, operator commands, and concrete feature code. Reusable algorithms and resource assembly
must not be reimplemented there.

Applications retain no framework feature base or framework `host` package. Their concrete features
extend the corresponding FeatureFramework platform base directly, while bootstrap composition selects
an optional DataRegistry plugin. Compatibility accessors, DataRegistry discovery/gate plumbing,
player-reference resolution, proxy access, DataProvider discovery, feature contexts,
configuration/localization handlers, resource managers, graph loaders, descriptors, registries, and
capability implementations are framework-owned.

## Compatibility and packaging

Platform dependencies use `provided` scope. Consumers shade framework artifacts but do not embed the
Paper or Velocity API. Public framework packages use `nl.hauntedmc.featureframework`; consumer domain
APIs retain their own namespaces. Releases publish normal, source, and Javadoc artifacts.

## Acceptance boundary

The `platform-acceptance` Maven profile packages independent dummy Paper and Velocity plugins. Each
plugin hosts a two-feature collection: a provider publishes a public capability and a consumer declares
and resolves it. The fixture then reloads the provider graph and asserts dependent recreation, stable
capability-reference generation changes, `FeatureCatalogListener` transitions, and clean host shutdown
against pinned real platform runtimes. This verifies the framework as a standalone consumer dependency,
not merely as code reached through an application project.
