# FeatureFramework

FeatureFramework is a Java 25 library for building modular Paper and Velocity plugins. It lets one plugin contain multiple independently managed features without pushing every command, listener, task, integration, and service into one global bootstrap.

FeatureFramework is a library, not a Minecraft plugin. Your project provides the Paper or Velocity entry point and shades the framework into its plugin JAR.

## What it gives you

- **Feature lifecycle** — enable, disable, reload, and recreate individual parts of a plugin.
- **Owned resources** — commands, listeners, tasks, caches, GUIs, data resources, and services can be tied to the feature that created them and cleaned up with it.
- **Explicit relationships** — declare required or optional features, external plugins, capabilities, and internal services.
- **Feature-scoped infrastructure** — configuration, localization, logging, scheduling, commands, listeners, caching, and other toolkit components are available without application-wide singletons.
- **Paper and Velocity support** — the same feature model on both platforms, with platform-specific behavior where it matters.

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
var welcome = FeatureDefinition
        .<PaperFeature<Plugin, Void>, PaperFeatureContext<Plugin, Void>>builder(
                "Welcome", "1.0.0", WelcomeFeature.class, WelcomeFeature::new)
        .enabledByDefault()
        .build();

featureHost = PaperFeatureHost.builder(
        this,
        MyPlugin.class,
        FeatureCollection.of(welcome)
).build();
featureHost.start();
```

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

Contributor, release, and compatibility details are documented in [CONTRIBUTING.md](CONTRIBUTING.md). For internals, see [Architecture](docs/ARCHITECTURE.md) and [Threading](docs/THREADING.md).
