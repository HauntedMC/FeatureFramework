# Maintaining FeatureFramework

## Stability levels

FeatureFramework uses three conceptual stability levels:

1. **Consumer API** — supported application-facing contracts. Source and binary compatibility matter across compatible
   releases.
2. **Framework SPI** — public only because shared, Paper, and Velocity are separate artifacts. Namespace:
   `nl.hauntedmc.featureframework.spi.*`. This is framework plumbing, not a promise that consumers should implement it.
3. **Internal implementation** — implementation details that should live under `internal.*` where practical and may be
   refactored without becoming consumer contracts.

Do not move an existing public type in a minor release simply to improve package aesthetics. Keep the old FQCN as a
compatibility facade/delegate and schedule removal for a major release if necessary.

## Change workflow

For non-trivial refactors:

1. add or strengthen characterization tests;
2. preserve current public behavior and platform semantics;
3. make the smallest architectural extraction that removes real duplication;
4. run unit tests and architecture tests;
5. run platform acceptance;
6. run public API compatibility;
7. rebuild ServerFeatures and ProxyFeatures against the candidate.

## Architecture rules

- `api` is dependency-free framework contract code;
- `shared` cannot import Paper/Bukkit or Velocity;
- Paper and Velocity cannot import each other;
- toolkit cannot depend upward on platform adapters or optional integrations;
- lifecycle affinity and task scheduling are separate concerns;
- optional integration implementations remain isolated;
- native event, command, scheduler, logging, and UI semantics are not flattened merely to reduce line count.

## Release gate

A 1.2.x release is not ready if either migrated consumer needs a semantic workaround, Paper callbacks can run off the
primary thread, Velocity gains a synthetic thread hop, resource cleanup leaks, or API compatibility reports an
unexpected break.
