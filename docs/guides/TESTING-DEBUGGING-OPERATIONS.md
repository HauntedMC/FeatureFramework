# Testing, Debugging, and Operations

FeatureFramework is designed for dynamic lifecycle operations, so tests should verify more than first startup.

## Test three levels

### Domain tests

Most feature logic should remain normal Java. Test services, repositories, parsers, policies, and calculators without a Minecraft runtime whenever possible.

### Feature tests

Verify the feature's lifecycle contract:

- initialization registers the expected resources;
- invalid required state fails startup;
- cleanup can run after partial initialization;
- configuration application returns the intended reload result;
- optional dependencies genuinely remain optional.

The repository includes `featureframework-testkit` and `featureframework-mockito-testkit` for reusable fixtures where appropriate.

### Host/graph tests

For applications with many features, test:

- dependency ordering;
- required-dependency failure propagation;
- optional integration absence;
- capability availability and withdrawal;
- graph reload after configuration changes;
- no duplicate resources after repeated reloads.

## Operational checks

When a feature misbehaves, first determine which layer failed:

1. **definition** — wrong dependency/capability declaration;
2. **construction** — context or constructor failure;
3. **initialization** — feature domain/platform startup failure;
4. **runtime** — task/listener/command/service behavior;
5. **cleanup** — untracked resources or domain shutdown failure.

The framework's feature logger and runtime/catalog state are more useful when each feature name is stable and descriptive.

## Reload testing

A production-grade feature should survive this sequence in a test/staging server:

```text
start host
use feature
reload or disable feature
verify ingress stopped
verify resources removed
re-enable/recreate feature
use feature again
stop host
```

Run it more than once. Many registration leaks only appear on the second cycle.

## Threading

Do not infer threading from examples. Read [THREADING.md](../THREADING.md), especially before mixing lifecycle changes with asynchronous database/network work.

## Framework repository verification

For FeatureFramework itself:

```shell
./mvnw clean verify
./mvnw -Prelease clean verify
./mvnw -Pplatform-acceptance clean verify
```

The platform acceptance profile boots independent dummy consumers against pinned real runtimes and checks lifecycle/resource behavior.
