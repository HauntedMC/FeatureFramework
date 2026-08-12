# Dependencies, Capabilities, and Services

FeatureFramework provides several relationship types because they solve different problems. Use the narrowest one that expresses your intent.

## Required feature dependency

Use `requiresFeatures("Name")` when the consumer depends on the **lifecycle and identity of a specific feature**.

```java
.requiresFeatures("Profiles")
```

The dependency participates in graph ordering. The consumer should not run without that feature.

Use `optionallyUsesFeatures(...)` when the relationship enhances behavior but is not required for startup.

## External plugin dependency

Use `requiresPlugins("PlaceholderAPI")` when a feature cannot work without a separately installed platform plugin.

This is different from a feature dependency: FeatureFramework does not own the external plugin's lifecycle.

Keep the dependency on the smallest feature that needs it rather than making the entire application depend on the integration.

## Capability

A capability is the best choice when the consumer needs **a contract**, not a particular implementation.

Example:

```java
public interface PlayerProfileApi {
    Optional<PlayerProfile> find(UUID playerId);
}
```

Provider definition:

```java
.providesCapabilities(PlayerProfileApi.class)
```

Consumer definition:

```java
.requiresCapabilities(PlayerProfileApi.class)
```

Consumer implementation:

```java
PlayerProfileApi profiles = requireCapability(PlayerProfileApi.class);
```

Required capability relationships can be used by manifest discovery to derive graph dependencies, allowing implementations to change without hard-coding feature names into consumers.

Use `optionallyUsesCapabilities(...)` plus `findCapability(...)` for optional integrations.

### Capability design rules

- publish a small interface, not an implementation class;
- keep domain contracts free from Paper/Velocity types when cross-platform reuse is valuable;
- do not expose mutable implementation internals;
- make availability/lifetime follow the provider feature;
- prefer one cohesive API over a bag of unrelated methods.

## Internal service

Internal services represent implementation-level collaboration inside one application. Declare them with `providesInternalServices`, `requiresInternalServices`, or `optionallyUsesInternalServices` and resolve them through `requireInternalService`/`findInternalService`.

Choose an internal service when the contract is not intended as a reusable public extension point.

## Owned service publication

Each feature context also exposes a feature service manager (`services()` / the platform resource API manager). Services published through the owned scope are withdrawn during feature cleanup before domain state is released.

That lifecycle guarantee is the important part: consumers should never keep calling a service whose provider is already shutting down.

## Decision table

| Need | Use |
|---|---|
| Must start after a specific feature | required feature dependency |
| Optional behavior from a specific feature | optional feature dependency |
| Requires an installed third-party plugin | plugin dependency |
| Needs a reusable interface, implementation does not matter | capability |
| Optional reusable interface | optional capability |
| Private collaboration between features in one application | internal service |

## Avoid dependency chains based on convenience

A common anti-pattern is making Feature C require Feature B only because B can reach A. C should declare what it actually needs. Explicit relationships make graph reloads and future refactors predictable.
