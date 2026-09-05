# Dependencies, Capabilities, and Services

Use the relationship that matches what the consumer actually needs. This keeps the feature graph understandable and prevents unnecessary coupling.

## Feature dependencies

Use `requiresFeatures = "Profiles"` in `@FeatureDeclaration` when a feature specifically depends on the lifecycle of another named feature.

```java
@FeatureDeclaration(name = "Chat", version = "1.0.0", requiresFeatures = "Profiles")
```

Use `optionallyUsesFeatures = "DiscordBridge"` when the other feature only adds optional behavior.

## External plugin dependencies

Use `requiresPlugins = "PlaceholderAPI"` when one feature cannot work without a separately installed platform plugin.

Keep the dependency on the feature that actually needs the plugin instead of making the entire application depend on it.

## Capabilities

Use a capability when the consumer needs an **interface**, not a particular feature implementation.

```java
public interface PlayerProfileApi {
    Optional<PlayerProfile> find(UUID playerId);
}
```

The provider declares and registers the capability:

```java
@FeatureDeclaration(
        name = "Profiles",
        version = "1.0.0",
        providesCapabilities = PlayerProfileApi.class)

// Provider initialize()
services().publish(PlayerProfileApi.class, profileService);
```

A required consumer declares and resolves it:

```java
@FeatureDeclaration(
        name = "Chat",
        version = "1.0.0",
        requiresCapabilities = PlayerProfileApi.class)

// Consumer initialize()
PlayerProfileApi profiles = services().require(PlayerProfileApi.class);
```

For optional behavior, declare `optionallyUsesCapabilities(...)` and retain a reload-safe reference:

```java
ServiceRef<DiscordApi> discord = services().reference(DiscordApi.class);

// Resolve once per independent operation; the provider may change during a feature reload.
discord.get().ifPresent(api -> api.send(message));
```

When optional-provider availability should attach and detach resources, let the feature scope own
the entire integration lifecycle:

```java
services().integrate(DiscordApi.class, api -> {
    AutoCloseable subscription = api.subscribe(this::forwardMessage);
    return subscription; // closed on provider replacement/removal and consumer shutdown
});
```

Required capability relationships can also be used by manifest discovery to derive the feature dependency needed for lifecycle sequencing.

Keep capability interfaces small and focused on domain behavior. Avoid exposing a database connection, implementation class, or mutable manager just to make it reachable from another feature.

## Internal services

Internal services work similarly, but are intended for private collaboration inside one application rather than a reusable public capability.

```java
@FeatureDeclaration(
        name = "Profiles",
        version = "1.0.0",
        providesInternalServices = ProfileStore.class)

// Provider initialize()
services().publish(ProfileStore.class, profileStore);

@FeatureDeclaration(name = "Chat", version = "1.0.0", requiresInternalServices = ProfileStore.class)

// Consumer initialize()
ProfileStore store = services().require(ProfileStore.class);
```

Use `optionallyUsesInternalServices(...)` with the same `services().reference(...)` or
`services().integrate(...)` operations for optional collaboration.

## One guarded programming interface

Feature implementations have one service boundary: `services()`.

| Declaration | Valid operation | Meaning |
|---|---|---|
| `requiresCapabilities` / `requiresInternalServices` | `services().require(Type.class)` | provider must be active |
| `optionallyUsesCapabilities` / `optionallyUsesInternalServices` | `services().reference(Type.class)` | reload-safe optional lookup |
| optional declaration | `services().integrate(Type.class, factory)` | owned attach/detach lifecycle |
| `providesCapabilities` / `providesInternalServices` | `services().publish(Type.class, provider)` | staged provider publication |

The framework rejects an operation that disagrees with the declaration. A typo or undeclared
cross-feature dependency therefore fails at the feature boundary instead of silently coupling two
features through a global registry.

## Service lifetime

Published services are staged during initialization, activated as one lifecycle step, and removed
when their provider stops. Retain required services only while the consumer is active. For optional
services, retain `ServiceRef<T>`, not the implementation returned by `get()`.

## Runtime operations

`PaperFeatureHost` and `VelocityFeatureHost` implement `FeatureFrameworkApi<String>`. Application/admin code can inspect feature state and perform enable, disable, soft reload, feature reload, and graph reload operations through the host instead of reaching into loader internals.

## Which one should I use?

| Need | Use |
|---|---|
| Must run with a specific named feature | required feature dependency |
| Optional behavior from a specific named feature | optional feature dependency |
| Needs a separately installed plugin | plugin dependency |
| Needs a reusable interface | capability |
| Needs a private cross-feature contract inside one application | internal service |

Declare the relationship directly. Avoid chains such as “C depends on B because B can reach A” when C actually depends on A.
