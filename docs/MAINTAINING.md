# Maintaining FeatureFramework

## Stability levels

FeatureFramework uses three conceptual stability levels:

1. **Consumer API** — supported application-facing contracts. Source and binary compatibility matter across compatible
   releases.
2. **Framework SPI** — cross-artifact plumbing that must be public only because shared, Paper, and Velocity are separate
   Maven artifacts. When a genuine seam is needed, place it under `nl.hauntedmc.featureframework.spi.*`; do not create
   an SPI wrapper merely to repackage an existing shared implementation.
3. **Internal implementation** — implementation details that should live under `internal.*` where practical and may be
   refactored without becoming consumer contracts.

Existing public types keep their published FQCN in minor releases. A package smell alone is not enough reason to add a
second public facade or compatibility hierarchy; defer cosmetic public package moves to a major release.

## Change workflow

For non-trivial refactors:

1. add or strengthen characterization tests;
2. preserve current public behavior and platform semantics;
3. make the smallest architectural extraction that removes real duplication;
4. verify that the extraction owns behavior rather than simply forwarding every method to an existing shared class;
5. run unit tests and architecture tests;
6. run platform acceptance;
7. run public API compatibility;
8. rebuild ServerFeatures and ProxyFeatures against the candidate.

## Architecture rules

- `api` is dependency-free framework contract code;
- `shared` cannot import Paper/Bukkit or Velocity;
- Paper and Velocity cannot import each other;
- toolkit cannot depend upward on host, lifecycle, integration, or platform adapters;
- lifecycle affinity and task scheduling are separate concerns;
- optional integration implementations remain isolated;
- native event, command, scheduler, logging, and UI semantics are not flattened merely to reduce line count;
- prefer an existing shared state machine over another layer that only delegates to it.

## Release gate

A 1.2.x release is not ready if either migrated consumer needs a semantic workaround, Paper callbacks can run off the
primary thread, Velocity gains a synthetic thread hop, resource cleanup leaks, public API compatibility reports an
unexpected break, or a new abstraction expands the public surface without centralizing real policy.
