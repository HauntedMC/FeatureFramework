# Toolkit and Component Index

FeatureFramework is larger than its feature host. Use this index to find the right subsystem without reading the entire source tree.

> Prefer the highest-level feature-owned adapter that fits your use case. Lower-level toolkit classes exist for composition and testing, but application features should keep ownership visible.

## Core public API — `featureframework-api`

Use for stable runtime-facing contracts and models:

- feature identifiers/catalog models;
- feature runtime state and failures;
- capability registry contracts;
- shared public API types that should not depend on Paper or Velocity.

## Host and feature model — `featureframework-shared`

Key areas:

- `host` — `FeatureDefinition`, `FeatureCollection`, managed contexts, host composition;
- `feature` — feature contracts and lifecycle-aware base behavior;
- `loader` — descriptor/manifest discovery and dependency graph loading;
- `runtime` — runtime state and feature lifecycle coordination;
- `lifecycle` — ownership trackers, cleanup sequences, resource state;
- `service` — internal and feature-owned service publication;
- `config` — feature configuration handling and reload results;
- `localization` — platform-neutral localization contracts.

## Shared toolkits

The shared module also contains reusable platform-neutral toolkit packages. Consult their Javadocs/source when you need implementation-specific details. Major areas include:

- safe configuration and localization I/O;
- logging abstractions;
- cache/resource helpers;
- HTTP/network helpers;
- text/token utilities;
- reusable testing-oriented utilities.

Do not import a utility merely because it exists; keep feature code dependent on the narrowest useful abstraction.

## Paper — `featureframework-paper`

The Paper adapter adds feature-owned/native integrations including:

- `paper.host` — `PaperFeature`, `PaperFeatureContext`, `PaperFeatureHost`, host composition;
- `paper.lifecycle` — feature-owned task/listener/resource managers;
- `paper.command` — command ownership and Brigadier integration;
- `paper.localization` — Paper message rendering/localization;
- `paper.log` — feature-scoped logging;
- `paper.ui` — UI/inventory/menu helpers;
- packet, registry, clock/time, preview, and toast adapters where applicable.

`PaperFeatureResources` is the main ownership gateway for tasks, commands, listeners, data, caches, GUIs, and feature services.

## Velocity — `featureframework-velocity`

The Velocity adapter provides the same application architecture with proxy-native behavior:

- `velocity.host` — `VelocityFeature`, context, host, host composition;
- lifecycle/resource ownership for Velocity tasks/listeners/commands;
- Velocity command/Brigadier adapters;
- localization and feature logging;
- connection/network utilities.

Use `VelocityFeatureContext.proxy()` when native `ProxyServer` access is required.

## Test support

- `featureframework-testkit` — reusable proxies and filesystem fixtures.
- `featureframework-mockito-testkit` — optional Mockito support for suites that need it.

## Where to learn an API

1. Find the subsystem here.
2. Read the relevant platform example.
3. Read the public class/interface Javadocs/source for exact overloads.
4. For runtime semantics, use [ARCHITECTURE.md](../ARCHITECTURE.md) and [THREADING.md](../THREADING.md).

The examples intentionally demonstrate stable high-level extension points rather than attempting to mirror every helper method in the source tree.
