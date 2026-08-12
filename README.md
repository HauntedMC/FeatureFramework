# FeatureFramework

FeatureFramework is a Java 25 framework for building **modular Paper and Velocity plugins** from independently managed features.

Instead of growing one plugin class into a collection of global managers, listeners, commands, tasks, caches, and integrations, you split the application into features with explicit lifecycle ownership and explicit relationships.

A feature can be enabled, disabled, reloaded, composed with other features, and cleaned up without leaving its resources behind.

## Why use it?

FeatureFramework is useful when a plugin is becoming a platform rather than a single mechanic.

It gives you:

- **Feature isolation** — each feature owns its commands, listeners, tasks, caches, GUIs, data resources, and services.
- **Reliable cleanup** — framework-owned resources are quiesced and released when a feature stops or reloads.
- **Dependency graphs** — express required/optional feature, plugin, capability, and internal-service relationships instead of hand-ordering startup.
- **Composable APIs** — publish capabilities between independently reusable features and internal services between features in the same application.
- **Configuration and localization** — feature-scoped configuration and messages are part of the feature lifecycle.
- **Paper and Velocity parity** — the same architecture and shared contracts, with platform-native adapters where behavior must differ.
- **Large-plugin maintainability** — add or remove a feature without turning the bootstrap class into the application.

FeatureFramework is a **library**, not a Minecraft plugin. Your project supplies the Paper or Velocity bootstrap and shades the framework into its own plugin JAR.

## The mental model

```text
Your plugin bootstrap
        |
        v
FeatureHost
        |
        +--> Feature A --> owned commands/listeners/tasks/config/services
        |
        +--> Feature B --> owned commands/listeners/tasks/config/services
        |
        +--> Feature C --> requires capability from A
```

A feature normally consists of:

1. a `FeatureDefinition` describing identity and relationships;
2. a `PaperFeature` or `VelocityFeature` implementation;
3. resources registered through its feature context;
4. optional configuration, messages, capabilities, or services.

Start with [The Feature Mental Model](docs/concepts/FEATURE-MENTAL-MODEL.md) before reading the architecture internals.

## Requirements

- Java 25
- Maven 3.9+ (the checked-in wrapper is recommended)
- Paper or Velocity for platform integration

## Add FeatureFramework

Paper (Bukkit-side):

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

For GitHub Packages:

```xml
<repository>
  <id>github</id>
  <url>https://maven.pkg.github.com/HauntedMC/FeatureFramework</url>
</repository>
```

Platform APIs are `provided`; your application chooses its Paper/Velocity version. Shade FeatureFramework into the final plugin JAR and **do not relocate FeatureFramework packages**, because public extension types must preserve their published class identity.

## Your first feature

A managed Paper feature is intentionally small:

```java
public final class WelcomeFeature extends PaperFeature<MyPlugin, Void> {
    public WelcomeFeature(PaperFeatureContext<MyPlugin, Void> context) {
        super(context);
    }

    @Override
    public void initialize() {
        logger().info("Welcome feature enabled");
    }

    @Override
    public void disable() {
        // Only release domain state you own directly.
        // Framework-owned resources are cleaned up by the feature lifecycle.
    }
}
```

Describe it once:

```java
FeatureDefinition<PaperFeature<MyPlugin, Void>, PaperFeatureContext<MyPlugin, Void>> welcome =
        FeatureDefinition.<PaperFeature<MyPlugin, Void>, PaperFeatureContext<MyPlugin, Void>>builder(
                        "Welcome", "1.0.0", WelcomeFeature.class, WelcomeFeature::new)
                .enabledByDefault()
                .build();
```

Then put definitions in a `FeatureCollection` and start a `PaperFeatureHost` from the plugin bootstrap. Velocity uses the equivalent `VelocityFeature`, `VelocityFeatureContext`, and `VelocityFeatureHost` types.

The examples show the complete composition rather than hiding it in the README.

## Choose a learning path

| Goal | Start here |
|---|---|
| Build my first Paper feature | [`examples/paper`](examples/paper/README.md) |
| Build my first Velocity feature | [`examples/velocity`](examples/velocity/README.md) |
| Structure a plugin with many features | [Multi-feature plugin design](docs/concepts/MULTI-FEATURE-PLUGIN-DESIGN.md) |
| Understand cleanup and reload safety | [Lifecycle and resource ownership](docs/concepts/LIFECYCLE-AND-RESOURCES.md) |
| Share functionality between features | [Dependencies, capabilities, and services](docs/concepts/DEPENDENCIES-CAPABILITIES-SERVICES.md) |
| Add config and translated messages | [Configuration and localization](docs/guides/CONFIGURATION-AND-LOCALIZATION.md) |
| Migrate an existing large plugin | [Migration](docs/MIGRATION.md) and [migration strategy](docs/guides/MIGRATING-AN-EXISTING-PLUGIN.md) |
| Find a framework subsystem | [Toolkit and component index](docs/reference/TOOLKIT-INDEX.md) |
| Understand internals | [Architecture](docs/ARCHITECTURE.md) |
| Understand thread guarantees | [Threading](docs/THREADING.md) |

See the complete [documentation map](docs/README.md).

## Modules

- `featureframework-api` — stable, dependency-free public runtime API and capability contracts.
- `featureframework-shared` — feature model, host/runtime composition, lifecycle, graph loading, config/localization, services, and platform-neutral toolkits.
- `featureframework-paper` — Paper host/context plus feature-owned scheduling, commands, listeners, GUIs, packets, registries, clocks, previews, and UI adapters.
- `featureframework-velocity` — Velocity host/context plus feature-owned scheduling, commands, listeners, logging, and network utilities.
- `featureframework-testkit` — reusable interface proxies and filesystem test fixtures.
- `featureframework-mockito-testkit` — opt-in Mockito test support.

`shared` depends on `api`; platform modules depend on `shared`; Paper and Velocity adapters do not depend on each other. Architecture tests enforce these boundaries.

## Build and verify

```shell
./mvnw clean verify
```

Release-equivalent artifact verification:

```shell
./mvnw -Prelease clean verify
```

Platform acceptance consumers:

```shell
./mvnw -Pplatform-acceptance clean verify
```

Publishing is tag-driven. Set `<revision>` in the root `pom.xml`, then push the matching `vMAJOR.MINOR.PATCH` tag. Pull requests also run public API compatibility checks against the published baseline.

## Deeper documentation

- [Documentation map](docs/README.md)
- [Architecture](docs/ARCHITECTURE.md)
- [Threading](docs/THREADING.md)
- [Migration](docs/MIGRATION.md)
- [Examples](examples/README.md)
- [Contributing](CONTRIBUTING.md)
- [Security](SECURITY.md)
