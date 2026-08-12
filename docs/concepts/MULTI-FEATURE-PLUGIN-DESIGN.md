# Multi-feature Plugin Design

FeatureFramework pays off most when a plugin contains several independently meaningful systems.

## Keep the bootstrap boring

A healthy bootstrap performs platform initialization, assembles definitions, creates the feature host, and forwards enable/disable.

It should not know how a punishment is stored, how a lobby menu is rendered, or how a queue listener works.

## Compose definitions centrally

Create one class whose job is application composition:

```java
public final class Features {
    private Features() {}

    public static FeatureCollection<PaperFeature<MyPlugin, Void>, PaperFeatureContext<MyPlugin, Void>> all() {
        return FeatureCollection.of(
                ProfilesFeature.definition(),
                ChatFeature.definition(),
                ModerationFeature.definition(),
                LobbyFeature.definition()
        );
    }
}
```

The exact location is less important than having one obvious map of the application.

## Example architecture

```text
ProfilesFeature
    provides PlayerProfileApi
          |
          +------> ChatFeature
          |
          +------> ModerationFeature

EconomyBridgeFeature
    requires external economy plugin
    provides EconomyApi
          |
          +------> ShopFeature
```

This is better than one shared `PluginManager` object handed to every subsystem. Consumers declare only what they actually use.

## Feature size

Too large:

- one `CoreFeature` containing all gameplay and integrations;
- dozens of unrelated commands under one lifecycle;
- disable requires resetting the entire application.

Too small:

- one feature per listener;
- one feature per command handler;
- artificial dependency graphs between classes that share the same state/lifetime.

A good feature is a **cohesive lifecycle boundary**.

## Integrations should usually be features

Third-party integrations are especially good feature candidates:

- Discord bridge;
- PlaceholderAPI expansion;
- permissions/economy bridge;
- Redis-backed network synchronization;
- external HTTP service.

Only the integration feature needs the external plugin/library/resource. Other features depend on the capability it provides.

## Domain objects remain normal Java

Inside a feature, use normal composition:

```text
PunishmentsFeature
├── PunishmentRepository
├── PunishmentService
├── PunishmentListener
└── PunishmentCommand
```

Those classes do not need to implement FeatureFramework interfaces. The feature owns them and decides which contracts, if any, are published.

## Configuration boundaries

Keep configuration close to the feature that interprets it. A global application config can still exist for truly global host settings, but do not recreate a monolithic configuration object containing every feature's schema.

## Failure isolation

Required dependency failures should stop dependents. Optional integrations should degrade deliberately. Do not catch every startup exception and continue with half-initialized state.

## Growing from three to thirty features

As the application grows:

1. group packages by feature/domain;
2. keep definitions discoverable in one composition layer;
3. use capabilities for contracts intended to outlive a specific implementation;
4. use internal services for private cross-feature collaboration;
5. use required dependencies only when there is a real lifecycle dependency;
6. keep platform registrations inside the owning feature;
7. write feature-level tests around domain classes and host-level tests around graph/lifecycle behavior.

See the advanced multi-feature examples under [`examples/paper`](../../examples/paper/README.md) and [`examples/velocity`](../../examples/velocity/README.md).
