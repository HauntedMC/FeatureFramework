# The Feature Mental Model

FeatureFramework is easiest to understand when you separate **application composition** from **feature implementation**.

## The five pieces

### 1. Plugin bootstrap

Your normal Paper or Velocity entry point owns application startup and shutdown. Keep it thin. It should assemble the feature definitions, create the host, start it, and stop it.

It should not become the location where every command, listener, cache, database integration, and gameplay mechanic is registered.

### 2. `FeatureDefinition`

A definition is the declarative description of one feature. It tells the host:

- feature name and version;
- implementation constructor;
- whether it is enabled by default;
- startup order when a deterministic tie-break is useful;
- required and optional feature dependencies;
- required plugin dependencies;
- required, optional, and provided capabilities;
- required, optional, and provided internal services;
- optional classification and roles.

Definitions are application composition. Put them together in one obvious place rather than scattering dependency knowledge through feature constructors.

### 3. Feature implementation

On Paper, subclass `PaperFeature<P, D>`. On Velocity, subclass `VelocityFeature<P, D>`.

A feature should represent a coherent unit that an operator or developer can reason about independently: chat moderation, lobby navigation, punishments, queues, cosmetics, party integration, join messaging, maintenance mode, and so on.

It implements the normal feature contract:

- `initialize()` — acquire domain state and register resources;
- `disable()` — release domain state not already owned by framework resource managers;
- `getDefaultConfig()` — optional feature defaults;
- `getDefaultMessages()` — optional feature message defaults;
- `applyConfiguration()` — optionally support a soft configuration application instead of recreation.

### 4. Feature context

The host assembles a context for each feature instance. The context exposes the scoped systems the feature is allowed to use:

- plugin/bootstrap instance;
- descriptor metadata;
- configuration handler;
- localization;
- lifecycle/resource scope;
- feature logger;
- capability registry;
- internal service registry;
- owned service manager.

Velocity contexts additionally expose the configured `ProxyServer`.

This makes dependencies visible and testable instead of relying on global singletons.

### 5. Feature host/runtime

The host turns definitions into a running dependency graph. It controls startup, shutdown, enable/disable, reload behavior, capability visibility, service publication, and cleanup order.

Most application code should not need the lower-level runtime classes directly.

## A useful boundary test

Ask: **Could this functionality be disabled and cleaned up without disabling unrelated functionality?**

If yes, it is probably a good feature boundary.

If two pieces always share state, lifetime, and purpose, keep them in one feature until a real boundary appears.

## What FeatureFramework is not

It is not a dependency-injection container that should abstract every Java object. Plain constructor composition inside a feature is still good design.

It is not a reason to turn every class into a feature. Features are lifecycle/application boundaries; services, repositories, handlers, and domain objects can remain normal classes owned by a feature.

It is not a replacement for Paper or Velocity APIs. Platform adapters preserve native platform behavior while adding ownership and composition around it.

## Recommended package layout

```text
com.example.myplugin
├── MyPlugin.java                 # thin bootstrap
├── Features.java                 # FeatureDefinition collection
├── chat/
│   ├── ChatFeature.java
│   ├── ChatService.java
│   └── ...
├── moderation/
│   ├── ModerationFeature.java
│   └── ...
└── lobby/
    ├── LobbyFeature.java
    └── ...
```

For a very large plugin, one package per feature is usually clearer than organizing all listeners together, all commands together, and all managers together.

## Next

Read [Lifecycle and resource ownership](LIFECYCLE-AND-RESOURCES.md), then follow the examples for your platform.
