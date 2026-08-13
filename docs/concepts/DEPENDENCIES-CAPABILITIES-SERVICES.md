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
        classification = FeatureClassification.CAPABILITY_PROVIDER,
        providesCapabilities = PlayerProfileApi.class)

// Provider initialize()
getContext().services().registerService(PlayerProfileApi.class, profileService);
```

A required consumer declares and resolves it:

```java
@FeatureDeclaration(
        name = "Chat",
        version = "1.0.0",
        classification = FeatureClassification.CAPABILITY_CONSUMER,
        requiresCapabilities = PlayerProfileApi.class)

// Consumer initialize()
PlayerProfileApi profiles = requireCapability(PlayerProfileApi.class);
```

For optional behavior, use `optionallyUsesCapabilities(...)` and `findCapability(...)`.

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
getContext().services().registerInternalService(ProfileStore.class, profileStore);

@FeatureDeclaration(name = "Chat", version = "1.0.0", requiresInternalServices = ProfileStore.class)

// Consumer initialize()
ProfileStore store = requireInternalService(ProfileStore.class);
```

Use `optionallyUsesInternalServices(...)` with `findInternalService(...)` for optional collaboration.

## Service lifetime

Services registered through the feature service manager are removed when their provider stops. Do not keep implementation objects around after the provider feature has been disabled or recreated.

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
