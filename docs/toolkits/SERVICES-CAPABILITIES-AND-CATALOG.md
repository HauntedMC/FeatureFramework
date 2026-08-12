# Services, Capabilities, and the Feature Catalog

These APIs make a multi-feature application composable without a global service locator.

## Capabilities

Capabilities are reusable contracts visible through the host/runtime capability registry. Definitions declare required, optional, and provided capability types.

Consumers use:

- `requireCapability(Type.class)` when the definition declares it as required;
- `findCapability(Type.class)` for optional behavior.

Prefer interface contracts that describe domain behavior rather than implementation mechanics.

## Internal services

Internal services are for private collaboration inside one application. Definitions can require/provide them just like capabilities, while feature implementations resolve them with `requireInternalService` or `findInternalService`.

Use them when publishing a reusable extension contract would be unnecessary API surface.

## Owned service manager

The managed context exposes `services()`, and each platform resource scope exposes its feature API/service manager. Services published through the feature-owned path are withdrawn during cleanup before domain state is released.

The key guarantee is lifetime alignment: a callable service should not remain discoverable after its provider has begun teardown.

## Feature catalog/runtime API

The platform hosts implement `FeatureFrameworkApi<String>`. Application/admin code can inspect runtime state and the feature catalog and can perform feature enable, disable, soft reload, per-feature reload, and graph reload operations through the host façade.

Use the public façade for operational tooling rather than reaching into loader internals.

## Contract design

Good capability/service contract:

```java
public interface PartyApi {
    Optional<PartyView> partyOf(UUID playerId);
}
```

Poor contract:

```java
public interface PartyApi {
    PartyManagerImpl manager();
    Connection database();
}
```

The second example leaks implementation and resource ownership to consumers.

See [Dependencies, Capabilities, and Services](../concepts/DEPENDENCIES-CAPABILITIES-SERVICES.md) for relationship selection.
