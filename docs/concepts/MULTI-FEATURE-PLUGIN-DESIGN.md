# Multi-feature Plugin Design

For a larger plugin, keep bootstrap lifecycle separate from feature implementation, but keep each feature's
metadata with that feature. `@FeatureDeclaration` is the application graph; the compiler builds the catalog.

## Keep the bootstrap small

The Paper or Velocity entry point should build and start the host, not wire every subsystem itself.

```java
@GenerateFeatureCatalog(
        generatedClassName = "com.example.catalog.BuiltInFeatures",
        featurePackage = "com.example.features",
        featureBase = PaperFeature.class,
        featureContext = PaperFeatureContext.class)
public final class MyPlugin extends JavaPlugin {
    private PaperFeatureHost featureHost;

    @Override
    public void onEnable() {
        featureHost = PaperFeatureHost.builder(this, MyPlugin.class, BuiltInFeatures.collection()).build();
        featureHost.start();
    }

    @Override
    public void onDisable() {
        if (featureHost != null) featureHost.stop();
    }
}
```

## Declare relationships where they belong

The declaration is next to the feature implementation, which keeps reviews local without hiding the graph:

```java
@FeatureDeclaration(
        name = "Chat",
        version = "1.0.0",
        requiresCapabilities = PlayerProfileApi.class,
        enabledByDefault = true)
public final class ChatFeature extends PaperFeature<Plugin, Void> {
    // ...
}
```

The generated `BuiltInFeatures.definitions()` remains a deterministic, inspectable representation of the complete graph.

## Depend on contracts where possible

A typical larger application might look like this:

```text
ProfilesFeature
  provides PlayerProfileApi
       ├──> ChatFeature
       └──> ModerationFeature

EconomyBridgeFeature
  requires external economy plugin
  provides EconomyApi
       └──> ShopFeature
```

Only the integration feature needs to know how the external plugin or backend works. Other features use the capability it exposes.

## How large should a feature be?

Avoid both extremes:

- one `CoreFeature` containing unrelated systems is too broad;
- one feature per listener or command is too small.

A feature should represent one coherent responsibility with one meaningful lifetime. Normal repositories, services, listeners, commands, DTOs, and handlers stay as ordinary classes inside that feature.

Third-party integrations often make good features because they have clear startup, failure, and shutdown behavior: Redis, Discord, PlaceholderAPI, external HTTP services, permissions/economy bridges, and similar integrations.

## Scale from a plugin graph to a network application

At network scale, features are not merely a way to organize source folders. They become operational units: each can own a backend subscription, an external client, a capability, config, commands, and observability state, then be replaced without restarting unrelated systems.

```text
IdentityFeature                 NetworkTransportFeature
  owns DataRegistry readiness     owns DataProvider connections and event subscriptions
  provides PlayerIdentityApi      provides NetworkEventsApi
       ├──> SessionPolicyFeature        ├──> CapacityFeature
       └──> ModerationFeature           └──> AnnouncementFeature

FeatureAdmin
  observes the graph and invokes lifecycle operations
```

Keep data clients and repositories private to their owning feature. Publish a small capability representing behavior, not the raw client, then make consumers require that capability. When a provider is recreated, its consumers are recreated as necessary instead of continuing to use stale state.

An admin/control-plane command is also a legitimate feature: it can list and inspect the graph, offer completion from `FeatureCommandModel`, and call structured host operations. Give that command elevated permissions and decide whether it must be protected from disabling itself. See [Operating a large feature plugin](../guides/OPERATING-A-LARGE-FEATURE-PLUGIN.md) for the full pattern.

## Configuration and failures

Keep feature-specific config with the feature. Keep only truly global host settings outside it.

If a required dependency cannot start, let its dependents fail or remain unavailable. Optional integrations should degrade deliberately rather than leaving a feature half initialized.

See the full [Paper](../../examples/paper/06-multi-feature-plugin/README.md) and [Velocity](../../examples/velocity/06-multi-feature-plugin/README.md) composition examples.
