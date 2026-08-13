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

## Configuration and failures

Keep feature-specific config with the feature. Keep only truly global host settings outside it.

If a required dependency cannot start, let its dependents fail or remain unavailable. Optional integrations should degrade deliberately rather than leaving a feature half initialized.

See the full [Paper](../../examples/paper/06-multi-feature-plugin/README.md) and [Velocity](../../examples/velocity/06-multi-feature-plugin/README.md) composition examples.
