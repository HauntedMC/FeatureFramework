# Testing, Debugging, and Operations

A feature that starts once is not necessarily lifecycle-safe. Test startup, cleanup, and recreation.

## What to test

### Domain code

Keep most business logic as normal Java and test services, repositories, parsers, policies, and calculations without a Minecraft runtime where possible.

### Feature lifecycle

Check that:

- initialization registers the resources you expect;
- invalid required state fails clearly;
- cleanup works after partial initialization;
- optional dependencies really are optional;
- config reload behavior matches `applyConfiguration()`.

`featureframework-testkit` and `featureframework-mockito-testkit` provide reusable fixtures for framework test suites that need them.

### Multiple features

For a larger application, also test dependency ordering, required dependency failures, optional integrations, capability/service availability, and repeated graph reloads.

## A useful reload test

Run this sequence more than once on a test server:

```text
start host
use feature
disable or reload feature
verify listeners/commands/tasks/services are gone
re-enable feature
use it again
stop host
```

The second cycle often exposes registrations or state that the first startup does not.

## Debugging lifecycle problems

When a feature fails, narrow it down to one stage:

1. definition/dependency resolution;
2. context or construction;
3. `initialize()`;
4. normal runtime callbacks/tasks/services;
5. cleanup or `disable()`.

Stable feature names and feature-scoped logs make this much easier to follow.

## Threading

Read [THREADING.md](../THREADING.md) before combining lifecycle operations with asynchronous database, HTTP, or network work. Paper and Velocity deliberately have different execution contracts.

## Framework repository verification

When changing FeatureFramework itself, the normal verification command is:

```shell
./mvnw clean verify
```

Release and platform-acceptance workflows are documented with the contributor/release process rather than repeated throughout the user documentation.
