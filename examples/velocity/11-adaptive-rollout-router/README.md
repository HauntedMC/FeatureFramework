# 11 — Adaptive canary rollout router (Velocity)

This example turns a deployment concern into a managed feature. Backend agents publish health snapshots over DataProvider Redis messaging. The proxy keeps a bounded last-known snapshot, assigns a stable percentage of players to a canary, fails over when a target becomes unhealthy, exposes routing as a capability, and reports state through an owned command.

Unlike the Paper ContractBoard example, its critical event path is synchronous and cache-only: a connection event never waits for Redis, a database, or a future.

## The application boundary

```text
backend health agents
  publish example.backend-health.v1
              │
              ▼
AdaptiveRolloutFeature
├── feature-owned Redis MessagingDataAccess + Subscription
├── BackendHealthStore
│   ├── concurrent live snapshot
│   ├── freshness policy
│   └── JSON last-known snapshot
├── RolloutPolicy
│   ├── deterministic cohort assignment
│   ├── stable → canary → fallback decision
│   └── RolloutRoutingApi capability
├── RolloutListener (ServerPreConnectEvent)
├── /rolloutstatus
└── expiration task
```

The listener does not know how messages arrive or how snapshots persist. The Redis handler does not call Velocity. `RolloutPolicy` is pure Java. Those seams make concurrency and failure behavior reviewable.

## Routing policy

For a request to `survival-stable`, `RolloutPolicy` applies these rules in order:

1. Hash the player UUID into a stable bucket from 0–99.
2. If that bucket is inside `canary-percent` and the canary snapshot is fresh and healthy, use the canary.
3. Otherwise prefer the stable backend when it is fresh and healthy.
4. If stable is unhealthy, allow the healthy canary to absorb traffic.
5. Otherwise use the configured fallback.
6. If every snapshot is unhealthy or stale, deny instead of routing from old information.

The policy only applies to the configured stable target; other game modes pass through unchanged. A larger plugin could parse several policies from a `routing.groups` map without changing the lifecycle design.

## Freshness is more important than “has a cached value”

`BackendHealthStore` restores the last snapshot from a feature-owned JSON cache, but a restored entry is usable only while `observedAt + stale-after-seconds` is still in the future. Disk persistence improves restart continuity; it never turns old health into truth.

```text
Redis callback thread       Velocity event thread
        │                           │
        ├─ validate timestamp       ├─ read immutable health value
        ├─ ignore older snapshot    ├─ calculate deterministic cohort
        ├─ replace map entry        └─ allow, redirect, or deny
        └─ persist summary
```

The concurrent store is the handoff. There is no cross-thread access to a `Player`, `RegisteredServer`, or event object.

## Configuration and messages

The [example config](example-config.yml) separates transport, freshness, and routing policy:

```yaml
messaging:
  connection: hauntedmc
  channel: deployments.backend-health
health:
  stale-after-seconds: 15
routing:
  stable-server: survival-stable
  canary-server: survival-canary
  fallback-server: lobby
  canary-percent: 10
```

The feature also owns operator and player messages in [example-messages.yml](example-messages.yml). The command reports observed age as well as the publisher's healthy flag, because “healthy 90 seconds ago” is not an actionable status.

All settings return `RECREATE_REQUIRED` on soft reload. Recreation swaps the Redis subscription, policy, cache view, listener, command, capability generation, and expiration task together. This avoids running a new rollout percentage with old targets or channel settings.

## DataProvider lifecycle

`RolloutProxyPlugin` uses `VelocityFeatureResourcesFactory.withDataProvider(...)`. The feature asks its `DataProviderResources` for one Redis messaging access and keeps the returned `Subscription` as its only manually closed handle.

Shutdown order matters:

1. lifecycle quiescing prevents new owned work and withdraws `RolloutRoutingApi`;
2. `disable()` unsubscribes the logical Redis subscription;
3. framework cleanup closes the feature's DataProvider scope, cache manager, command, listener, and task resources;
4. a recreated feature subscribes on a new generation with the new channel/policy.

DataProvider owns the messaging provider; the feature closes the subscription it created from that provider. This is the same ownership rule you would use for an HTTP stream, third-party callback registration, or message consumer.

## Why routing is a capability

Other features may need the same decision without importing `AdaptiveRolloutFeature`—for example, a `/play` command, a party transfer workflow, or a maintenance evacuator. They should resolve `RolloutRoutingApi`, not reach into `BackendHealthStore` or retain the Redis access.

The public contract contains immutable values and domain behavior. Transport and cache implementation remain replaceable. A consumer holding a stable `CapabilityRef<RolloutRoutingApi>` automatically reaches the new provider generation after reload.

## Files worth reading in order

1. [`AdaptiveRolloutFeature.java`](AdaptiveRolloutFeature.java) — full subsystem wiring and lifetime.
2. [`BackendHealthStore.java`](BackendHealthStore.java) — concurrent freshness and disk-cache policy.
3. [`RolloutPolicy.java`](RolloutPolicy.java) — deterministic, testable selection logic.
4. [`RolloutListener.java`](RolloutListener.java) — synchronous cache-only Velocity adapter.
5. [`BackendHealthMessage.java`](BackendHealthMessage.java) — the wire boundary.
6. [`RolloutProxyPlugin.java`](RolloutProxyPlugin.java) — platform facade with a DataProvider contributor.

## What production code would add

Move `BackendHealthMessage` into a small versioned contracts artifact shared with backend publishers. Include publisher identity/epoch and monotonic sequence when multiple publishers or restarts can produce competing snapshots. Add metrics for stale snapshots, redirects by reason, denied routes, subscription recovery, and canary traffic share. Protect a mutation command with separate permissions and an audit sink if operators may change rollout percentage live.

None of these concerns require a larger bootstrap or global singleton. They remain components of the feature that owns the routing decision.
