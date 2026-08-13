# FeatureFramework

FeatureFramework is a Java 25 library for building modular Paper and Velocity plugins. It lets one plugin contain multiple independently managed features without pushing every command, listener, task, integration, and service into one global bootstrap.

FeatureFramework is a library, not a Minecraft plugin. Your project provides the Paper or Velocity entry point and shades the framework into its plugin JAR.

## What it gives you

- **Feature lifecycle** — enable, disable, reload, and recreate individual parts of a plugin.
- **Owned resources** — commands, listeners, tasks, caches, GUIs, data resources, and services can be tied to the feature that created them and cleaned up with it.
- **Explicit relationships** — declare required or optional features, external plugins, capabilities, and internal services.
- **Feature-scoped infrastructure** — configuration, localization, logging, scheduling, commands, listeners, caching, and other toolkit components are available without application-wide singletons.
- **Paper and Velocity support** — the same feature model on both platforms, with platform-specific behavior where it matters.

## Built for a plugin that behaves like an application

Large networks do not only need a cleaner startup class. They need a safe way to operate independently deployable subsystems while players are online: disable a failed integration, reconcile edited configuration, replace a data-backed feature without stale consumers, and inspect the active graph without guessing which registrations survived the last reload.

FeatureFramework makes that model explicit:

- lifecycle operations are serialized and return structured results for enable, disable, soft reload, feature recreation, and full graph reconciliation;
- feature-owned commands, listeners, tasks, caches, services, DataProvider resources, and platform adapters are cleaned with the feature generation that created them;
- capability contracts isolate consumers from backend, database, messaging, and third-party integration details;
- `FeatureCommandModel` and `FeatureOperationMessages` provide the building blocks for a permissioned in-game operations command rather than another global singleton;
- the shared text toolkit safely normalizes legacy and MiniMessage formats, supports explicit MiniMessage allowlists, sanitizes untrusted tags, autolinks URLs, serializes components, and provides reusable validation patterns.

See [Operating a large feature plugin](docs/guides/OPERATING-A-LARGE-FEATURE-PLUGIN.md) for the control-plane and data-integration pattern, and [Text, formatting, and safe player input](docs/toolkits/TEXT-AND-FORMATTING.md) for the text toolkit.

If your plugin is small and has one responsibility, you may not need this. FeatureFramework becomes useful when the plugin contains several systems that should have clear ownership and lifecycle boundaries.

## Requirements

- Java 25
- Maven 3.9+; the included Maven wrapper is recommended
- Paper or Velocity for platform integration

## Add the dependency

Paper:

```xml
<dependency>
  <groupId>nl.hauntedmc.featureframework</groupId>
  <artifactId>featureframework-paper</artifactId>
  <version>RELEASE_VERSION</version>
</dependency>
```

Velocity:

```xml
<dependency>
  <groupId>nl.hauntedmc.featureframework</groupId>
  <artifactId>featureframework-velocity</artifactId>
  <version>RELEASE_VERSION</version>
</dependency>
```

For GitHub Packages, add the repository to your Maven configuration and authenticate with package-read access:

```xml
<repository>
  <id>github</id>
  <url>https://maven.pkg.github.com/HauntedMC/FeatureFramework</url>
</repository>
```

Platform APIs are `provided`, so your application controls the Paper or Velocity version. Shade FeatureFramework into the final plugin JAR. Do not relocate FeatureFramework packages; public extension types need to keep their published class identity.

## Minimal Paper example

A feature is a normal managed class:

```java
public final class WelcomeFeature extends PaperFeature<Plugin, Void> {
    public WelcomeFeature(PaperFeatureContext<Plugin, Void> context) {
        super(context);
    }

    @Override
    public void initialize() {
        logger().info("Welcome enabled");
    }

    @Override
    public void disable() {
    }
}
```

Declare it and start the host from your plugin bootstrap:

```java
@GenerateFeatureCatalog(
        generatedClassName = "com.example.myplugin.catalog.BuiltInFeatures",
        featurePackage = "com.example.myplugin.features",
        featureBase = PaperFeature.class,
        featureContext = PaperFeatureContext.class
)
public final class MyPlugin extends JavaPlugin {
    @Override public void onEnable() {
        featureHost = PaperFeatureHost.builder(this, MyPlugin.class, BuiltInFeatures.collection()).build();
        featureHost.start();
    }
}

@FeatureDeclaration(name = "Welcome", version = "1.0.0", enabledByDefault = true)
public final class WelcomeFeature extends PaperFeature<MyPlugin, Void> {
    public WelcomeFeature(PaperFeatureContext<MyPlugin, Void> context) { super(context); }
}
```

`featureframework-processor` generates `BuiltInFeatures` while compiling. Configure it explicitly as a Maven
annotation processor; the generated catalog uses constructor references and does not scan the plugin JAR at runtime.

Call `featureHost.stop()` from `onDisable()`. See the [complete Paper example](examples/paper/01-simple-feature/README.md) or the [Velocity equivalent](examples/velocity/01-simple-feature/README.md).

## Where to go next

| I want to... | Read |
|---|---|
| Understand the basic model | [Feature mental model](docs/concepts/FEATURE-MENTAL-MODEL.md) |
| Build a Paper feature | [Paper examples](examples/paper/README.md) |
| Build a Velocity feature | [Velocity examples](examples/velocity/README.md) |
| Structure a plugin with many features | [Multi-feature plugin design](docs/concepts/MULTI-FEATURE-PLUGIN-DESIGN.md) |
| Understand cleanup and reloads | [Lifecycle and resources](docs/concepts/LIFECYCLE-AND-RESOURCES.md) |
| Share APIs between features | [Dependencies, capabilities, and services](docs/concepts/DEPENDENCIES-CAPABILITIES-SERVICES.md) |
| Use config and messages | [Configuration and localization](docs/guides/CONFIGURATION-AND-LOCALIZATION.md) |
| Operate a large, live feature graph | [Operating a large feature plugin](docs/guides/OPERATING-A-LARGE-FEATURE-PLUGIN.md) |
| Safely format, inspect, and serialize text | [Text and formatting](docs/toolkits/TEXT-AND-FORMATTING.md) |
| Find a toolkit or component | [Toolkit index](docs/reference/TOOLKIT-INDEX.md) |
| Migrate an existing plugin | [Migration guide](docs/guides/MIGRATING-AN-EXISTING-PLUGIN.md) |

The full documentation index is in [`docs/README.md`](docs/README.md).

## Modules

- `featureframework-api` — stable public runtime and capability contracts.
- `featureframework-shared` — feature model, host/runtime, lifecycle, dependency loading, services, config/localization, and shared toolkits.
- `featureframework-paper` — Paper host and platform adapters.
- `featureframework-velocity` — Velocity host and platform adapters.
- `featureframework-testkit` and `featureframework-mockito-testkit` — reusable test support.

The shared modules do not depend on Paper or Velocity, and the two platform modules do not depend on each other. Architecture tests enforce those boundaries.

## Working on FeatureFramework

Run the full verification suite with:

```shell
./mvnw clean verify
```

Contributor and compatibility details are documented in [CONTRIBUTING.md](CONTRIBUTING.md). For FeatureFramework
releases, see the [release process](docs/RELEASE.md). For internals, see [Architecture](docs/ARCHITECTURE.md) and
[Threading](docs/THREADING.md).
