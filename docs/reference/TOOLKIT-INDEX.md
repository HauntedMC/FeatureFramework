# Toolkit and Component Index

Use this page when you know what you want to build but are not sure which FeatureFramework package owns it.

## Shared feature/runtime APIs

`featureframework-api` and `featureframework-shared` contain the platform-neutral model:

- `host` — `FeatureDefinition`, `FeatureCollection`, managed contexts, host composition;
- `feature` — feature contracts and lifecycle-aware base classes;
- `loader` — manifest discovery and dependency graph loading;
- `runtime` — runtime state and lifecycle coordination;
- `lifecycle` — resource ownership and cleanup;
- `service` — capability/internal-service registration and ownership;
- `config` — feature configuration and reload results;
- `localization` — shared localization contracts.

The shared toolkit also contains config/localization I/O, logging, cache helpers, HTTP/network utilities, text/token helpers, and testable utility classes.

## Paper

Useful areas in `featureframework-paper` include:

- `paper.host` — `PaperFeature`, context, host, and custom host composition;
- `paper.lifecycle` — owned tasks, listeners, and feature resource scope;
- `paper.command` — feature-owned commands and Brigadier integration;
- `paper.localization` and `paper.log` — platform message/logging adapters;
- `paper.ui` — inventory/menu UI support;
- packet, registry, time/clock, preview, and toast helpers.

`PaperFeatureResources` is the main entry point for feature-owned tasks, commands, listeners, data, caches, GUIs, and services.

## Velocity

Useful areas in `featureframework-velocity` include:

- `velocity.host` — `VelocityFeature`, context, host, and custom host composition;
- lifecycle/resource managers for tasks, listeners, commands, data, caches, and services;
- command/Brigadier adapters;
- localization and feature logging;
- connection/network utilities.

Use `VelocityFeatureContext.proxy()` when you need the native `ProxyServer`.

## Practical guides

- [Commands and listeners](../toolkits/COMMANDS-AND-LISTENERS.md)
- [Scheduling and async work](../toolkits/SCHEDULING-AND-ASYNC-WORK.md)
- [Data and caching](../toolkits/DATA-AND-CACHING.md)
- [Paper UI and platform adapters](../toolkits/PAPER-UI-AND-PLATFORM-ADAPTERS.md)
- [Configuration and localization](../guides/CONFIGURATION-AND-LOCALIZATION.md)
- [Dependencies, capabilities, and services](../concepts/DEPENDENCIES-CAPABILITIES-SERVICES.md)

For exact overloads, use the public class Javadocs/source. For lifecycle and threading semantics, use [Architecture](../ARCHITECTURE.md) and [Threading](../THREADING.md).
