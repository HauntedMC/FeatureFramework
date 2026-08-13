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

## Text, formatting, and message helpers

The shared text toolkit is useful well beyond chat formatting:

- `toolkit.text.format.TextFormatter` — normalize legacy, hex, MiniMessage, and plain strings; serialize to legacy or plain text; escape untrusted MiniMessage values.
- `toolkit.text.format.ComponentFormatter` — parse mixed input to Adventure `Component` values with an explicit MiniMessage feature allowlist, sanitization, custom tags, URL linking, and serializers for MiniMessage, legacy, plain text, and JSON.
- `toolkit.text.format.inspect.FormatInspector` — identify formatting in strings or components before applying a moderation, migration, or audit policy.
- `toolkit.text.placeholder.MessagePlaceholders` — immutable typed placeholder values with longest-key-first replacement.
- `toolkit.text.TextPatterns` — reusable compiled patterns for color codes, tags, URLs, Minecraft names, version/date strings, and validation.

Use the [text and formatting guide](../toolkits/TEXT-AND-FORMATTING.md) for trust-boundary examples and the supported conversion shapes.

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
- [Operating a large feature plugin](../guides/OPERATING-A-LARGE-FEATURE-PLUGIN.md)

For exact overloads, use the public class Javadocs/source. For lifecycle and threading semantics, use [Architecture](../ARCHITECTURE.md) and [Threading](../THREADING.md).
