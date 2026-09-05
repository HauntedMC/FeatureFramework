# Feature Mental Model

The main idea is simple: **your plugin owns a host, and the host owns features**.

```text
Plugin bootstrap
      |
      v
FeatureHost
  ├── ChatFeature
  ├── ModerationFeature
  └── LobbyFeature
```

Each feature has its own lifecycle and resources. The plugin bootstrap only assembles the application and starts or stops the host.

## The main pieces

### Plugin bootstrap

Keep the Paper or Velocity entry point small. It declares `@GenerateFeatureCatalog`, builds the host from the
generated collection, starts it, and stops it. Feature-specific listeners, commands, tasks, integrations, and
metadata belong in their feature class.

### `@FeatureDeclaration` and generated `FeatureDefinition`

A declaration describes how a feature fits into the application. The compiler generates its typed definition and
validates the constructor, identity, provider graph, and declared relationships such as:

- required or optional features;
- required external plugins;
- required, optional, or provided capabilities;
- required, optional, or provided internal services.

Declarations also specify a readable `FeatureStartupPhase`. Required providers must be in the same
or an earlier phase; discovery rejects a graph that contradicts its declared lifecycle order.
Dependencies remain the correct mechanism for ordering within a phase.

### Feature implementation

Use `PaperFeature<P>` on Paper and `VelocityFeature<P>` on Velocity.

The important lifecycle methods are:

- `initialize()` — create feature state and register resources;
- `disable()` — release state that FeatureFramework does not already own;
- `defaultConfig()` and `defaultMessages()` — optional feature defaults;
- `applyConfiguration()` — decide whether a config change can be applied live or needs recreation.

### Feature context

The context gives one feature access to its plugin, config, localization, logger, resource managers,
and a declaration-aware `services()` boundary. Velocity contexts also expose `ProxyServer`.

This is preferable to reaching into application-wide static managers because it makes ownership and dependencies visible.

### Feature host

The host loads the dependency graph and controls feature startup, shutdown, enable/disable, reloads, capabilities, and service lifetime. Most feature code only needs its context; it should not depend on loader/runtime internals.

## Choosing a feature boundary

A useful test is: **would it make sense to disable this functionality without disabling unrelated functionality?**

Good feature boundaries are things such as chat, moderation, queues, cosmetics, maintenance mode, a Discord bridge, or a Redis-backed network integration.

Do not make every class a feature. Repositories, services, listeners, command handlers, and domain objects can remain normal Java classes owned by one feature.

## Package by feature

For a larger plugin, this is usually easier to navigate:

```text
com.example.myplugin
├── MyPlugin.java
├── Features.java
├── chat/
│   ├── ChatFeature.java
│   ├── ChatService.java
│   ├── ChatListener.java
│   └── ChatCommand.java
└── moderation/
    ├── ModerationFeature.java
    └── ...
```

rather than putting every listener in one package, every command in another, and every manager in a third.

Next: [Lifecycle and resources](LIFECYCLE-AND-RESOURCES.md).
