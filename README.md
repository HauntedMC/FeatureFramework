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
  multi-feature host, host composition, lifecycle ownership trackers, graph/loading algorithms,
  capability/catalog implementations, owned service publication, startup/rollback coordination,
  administrative command models, safe YAML configuration, localization storage and component
  rendering, cache, HTTP, text, token, and testable utilities.
- `featureframework-paper` — ready-to-use Paper feature/host contexts, primary-thread lifecycle
  execution, schedulers, feature-owned commands and listeners, Brigadier dispatch/takeover, logging,
  packets, registries, clocks, UI, previews, and toast adapters.
- `featureframework-velocity` — ready-to-use Velocity feature/host contexts, direct lifecycle
  execution, schedulers, feature-owned commands and listeners, Brigadier adapters, command ownership,
  feature and structured connection logging, and network utilities.
- `featureframework-testkit` — reusable interface proxies and filesystem test fixtures.
- `featureframework-mockito-testkit` — opt-in coverage-friendly Mockito extension used by test suites
  that cannot use the inline mock maker.

The dependency rule is strict: `shared` depends on `api`, platform modules depend on `shared`, and
neither `api` nor `shared` knows a Minecraft platform. Paper and Velocity adapters must not depend on
each other. Architecture tests enforce these boundaries. No framework module contains application
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

Paper host lifecycle operations are synchronous and execute on Bukkit's primary thread even when the
caller is asynchronous. Velocity host lifecycle operations execute directly on the caller without a
synthetic main-thread hop. Scheduling APIs remain native to each platform. See
[Threading](docs/THREADING.md) and [Lifecycle](docs/LIFECYCLE.md) for the exact contracts.

Use `PaperFeatureHostComposition` or `VelocityFeatureHostComposition` when the application needs a
custom API version, localization policy, DataProvider resources, or optional DataRegistry discovery.
The public platform façades stay typed, while shared `FeatureHostComposition` owns their common scope
and host wiring.

Build everything with:

```shell
./mvnw clean verify
```

For a release candidate, run the same release-artifact profile used by GitHub from a clean checkout:

```shell
./mvnw -Prelease clean verify
```

Pull requests also run a public API compatibility gate against the `v1.0.0` baseline. Binary or source
incompatibilities in published framework types fail that gate rather than relying on review alone.

Publishing is tag-driven: first set `<revision>` in the root `pom.xml` to the intended release
version, then push the matching `vMAJOR.MINOR.PATCH` tag. GitHub runs the release build, the platform
acceptance gate, and then deploys the verified artifacts to GitHub Packages.

Compile and boot independent dummy Paper and Velocity consumers with pinned real runtimes using:

```shell
./mvnw -Pplatform-acceptance clean verify
```

The platform acceptance profile verifies graph reloads, platform execution semantics, and cleanup of
feature-owned tasks, listeners, commands, and services. It does not require Docker or an external
database.

## Documentation

- [Architecture](docs/ARCHITECTURE.md) — module ownership and dependency direction.
- [Threading](docs/THREADING.md) — Paper affinity and Velocity direct-execution guarantees.
- [Lifecycle](docs/LIFECYCLE.md) — feature/resource/task lifecycle contracts and teardown order.
- [Platform adapters](docs/PLATFORM-ADAPTERS.md) — what is shared and what deliberately remains native.
- [Toolkit](docs/TOOLKIT.md) — platform-neutral utility boundaries and formatting responsibilities.
- [Maintaining](docs/MAINTAINING.md) — compatibility, abstraction, validation, and release rules.
- [Migration guide](docs/MIGRATION.md) — consumer migration guidance.
- [Contributing](CONTRIBUTING.md) and [Security](SECURITY.md).
