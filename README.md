# FeatureFramework

FeatureFramework is a Java 25 library for building modular Paper and Velocity plugins. It is not a
Minecraft plugin: the application supplies the bootstrap and metadata, then embeds the framework in
its own shaded JAR.

## Requirements

- Java 25
- Maven 3.9+ (the checked-in wrapper is recommended)
- A Paper or Velocity plugin project for platform integration

## Modules

- `featureframework-api` — dependency-free public runtime API: feature catalog models, runtime
  state/failures, stable feature identifiers, and capability registry contracts.
- `featureframework-shared` — feature contracts, typed definitions and collections, the reusable
  multi-feature host, lifecycle and graph/loading algorithms, capability/catalog implementations, owned service
  publication, startup/rollback coordination, administrative command models, safe YAML configuration,
  localization storage and component rendering, cache, HTTP, text, token, and testable utilities.
- `featureframework-paper` — ready-to-use Paper feature/host contexts, schedulers, feature-owned
  commands and listeners, Brigadier dispatch/takeover, logging, packets, registries, clocks, UI,
  previews, and toast adapters.
- `featureframework-velocity` — ready-to-use Velocity feature/host contexts, schedulers,
  feature-owned commands and listeners, Brigadier adapters, command ownership, feature and structured
  connection logging, and network utilities.
- `featureframework-testkit` — reusable interface proxies and filesystem test fixtures.
- `featureframework-mockito-testkit` — opt-in coverage-friendly Mockito extension used by test suites
  that cannot use the inline mock maker.

The dependency rule is strict: `shared` depends on `api`, platform modules depend on `shared`, and
neither `api` nor `shared` knows a Minecraft platform. No framework module contains application
features, plugin bootstrap code, or domain capability contracts.

## Consumer setup

```xml
<dependency>
  <groupId>nl.hauntedmc.featureframework</groupId>
  <artifactId>featureframework-paper</artifactId>
  <version>RELEASE_VERSION</version>
</dependency>
```

Replace `RELEASE_VERSION` with the released version you are targeting. Use
`featureframework-velocity` for a Velocity plugin. Platform APIs are declared `provided`, so the
application controls its platform version. Shade the framework artifacts into the final plugin JAR;
FeatureFramework intentionally contains no `plugin.yml`, `paper-plugin.yml`, or Velocity `@Plugin`.

For Maven projects using GitHub Packages, configure the repository in the consuming build and
authenticate with a token that has package-read access:

```xml
<repository>
  <id>github</id>
  <url>https://maven.pkg.github.com/HauntedMC/FeatureFramework</url>
</repository>
```

Keep FeatureFramework packages unrelocated: public extension types must retain their published class
identity.

## Minimal Paper host

Define individual features with the shared typed model, then compose them into one collection. Required
capability relationships become graph dependencies automatically:

```java
FeatureDefinition<PaperFeature<Plugin, Void>, PaperFeatureContext<Plugin, Void>> producer =
        FeatureDefinition.<PaperFeature<Plugin, Void>, PaperFeatureContext<Plugin, Void>>builder(
                        "Producer", "1.0.0", ProducerFeature.class, ProducerFeature::new)
                .providesCapabilities(GreetingApi.class)
                .enabledByDefault()
                .build();
FeatureDefinition<PaperFeature<Plugin, Void>, PaperFeatureContext<Plugin, Void>> consumer =
        FeatureDefinition.<PaperFeature<Plugin, Void>, PaperFeatureContext<Plugin, Void>>builder(
                        "Consumer", "1.0.0", ConsumerFeature.class, ConsumerFeature::new)
                .requiresCapabilities(GreetingApi.class)
                .enabledByDefault()
                .build();

FeatureCollection<PaperFeature<Plugin, Void>, PaperFeatureContext<Plugin, Void>> features =
        FeatureCollection.of(producer, consumer);
```

A Paper bootstrap starts and stops the collection with:

```java
host = PaperFeatureHost.builder(this, MyPluginApi.class, features).build();
host.start(); // onEnable
host.stop();  // onDisable
```

Velocity uses the equivalent `VelocityFeature`, `VelocityFeatureContext`, and
`VelocityFeatureHost`. Both hosts implement `FeatureFrameworkApi<String>` and expose enable,
disable, soft reload, graph reload, stable capability references, and catalog subscriptions. Feature
implementations register commands, listeners, tasks, caches, and services through their scoped context;
the host releases those resources on reload or shutdown.

Use `PaperFeatureHostComposition` or `VelocityFeatureHostComposition` when the application needs a
custom API version, localization policy, DataProvider resources, or optional DataRegistry discovery.
Those compositions keep context creation, graph ownership, dependency checks, and resource lifecycle
inside FeatureFramework.

Build everything with:

```shell
./mvnw clean verify
```

For a release candidate, run the same release-artifact profile used by GitHub from a clean checkout:

```shell
./mvnw -Prelease clean verify
```

Publishing is tag-driven: first set `<revision>` in the root `pom.xml` to the intended release
version, then push the matching `vMAJOR.MINOR.PATCH` tag. GitHub runs the release build, the platform
acceptance gate, and then deploys the verified artifacts to GitHub Packages.

Compile and boot independent dummy Paper and Velocity consumers with pinned real runtimes using:

```shell
./mvnw -Pplatform-acceptance clean verify
```

The platform acceptance profile does not require Docker or an external database.

See [Architecture](docs/ARCHITECTURE.md), [Migration guide](docs/MIGRATION.md),
[Contributing](CONTRIBUTING.md), and [Security](SECURITY.md).
