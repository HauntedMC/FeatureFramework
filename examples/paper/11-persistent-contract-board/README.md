# 11 — Persistent ContractBoard application (Paper)

ContractBoard is a SQL-backed feature where players post and atomically claim contracts through a Brigadier command. It owns a MySQL connection, repository, hot snapshot, persistent last-known-good cache, async refresh task, join listener, localized messages, and a public capability.

## The complete runtime

```text
NetworkPlugin
└── ContractBoardFeature                         one availability/reload boundary
    ├── DataProvider scope: feature.ContractBoard
    │   └── MYSQL/system_data_rw → DataSource
    ├── ContractRepository                       transactional source of truth
    ├── ContractSnapshotCache
    │   ├── immutable in-memory read snapshot    fast player-facing reads
    │   └── cache/ContractBoard-snapshots/*.json last-known operational summary
    ├── ContractBoardService                     validation + async orchestration
    ├── ContractBoardApi                         reload-safe public behavior
    ├── /contracts                               command/presentation adapter
    ├── ContractJoinListener                     never waits for SQL
    └── async refresh task                       one owned scheduled generation
```

Keep this as one feature: the repository, service, command, listener, and cache have the same availability boundary. Splitting them into separate features would not give operators another useful control point.

## Follow one request through the system

When a player runs `/contracts claim <uuid>`:

1. `ContractCommand` parses the UUID and calls the capability implementation.
2. `ContractBoardService` submits JDBC work through the feature's task manager.
3. `ContractRepository.claim(...)` uses a conditional `UPDATE ... WHERE status = 'OPEN'`; two servers racing for the same contract cannot both win.
4. The service invalidates its hot snapshot.
5. The completion returns to the Paper thread through another feature-owned task before it touches a `CommandSender`.
6. The command renders a stable feature-local message key.

If the feature is disabled while a database operation is pending, its task scope is quiesced and the old command generation is already unregistered. `ContractCommand.onMain(...)` drops any late completion that can no longer be presented safely.

## Why both caches exist

The database remains authoritative. The in-memory snapshot absorbs frequent `/contracts` reads and is replaced as one immutable value, so readers never observe a half-refreshed list. The JSON cache stores only a non-authoritative count for join notices and startup diagnostics.

The consistency policy is then straightforward:

| Data | Source | Staleness allowed | Failure behavior |
|---|---|---:|---|
| Claim state | MySQL conditional update | none | command fails; never guesses |
| Open-contract list | MySQL, then hot snapshot | 20 seconds | next read retries MySQL |
| Join-notice count | hot snapshot or JSON summary | up to 7 days | hint may be stale; gameplay is unaffected |

Do not use the JSON cache to approve a claim or pay a reward. A cache is useful only after its authority and invalidation policy are named.

## Configuration is part of the runtime design

The generated [example config](example-config.yml) has separate database, cache, refresh, validation, and presentation sections:

```yaml
database:
  connection: system_data_rw
cache:
  hot-ttl-seconds: 20
  snapshot-size: 100
refresh:
  seconds: 30
contracts:
  max-reward: 50000
join-notice:
  minimum-open: 3
```

`applyConfiguration()` returns `RECREATE_REQUIRED` because changing the connection, cache policy, snapshot size, task cadence, or validation rules requires a new runtime generation. The host unregisters the old command/listener/capability, cancels and drains owned tasks, closes the feature's DataProvider scope, and then constructs the replacement.

A future version could apply `join-notice.minimum-open` live, but mixed partial-reload behavior should only be added when the extra complexity is worthwhile.

## Messages are presentation contracts

The feature declares defaults in `defaultMessages()` and uses placeholders at the command/listener boundary. See [example-messages.yml](example-messages.yml). Repository and service code return results and models, never formatted chat text, which keeps domain tests independent from Adventure and makes localization changes safe.

## DataProvider ownership

`NetworkPlugin` uses `PaperFeatureResourcesFactory.withDataProvider(...)`, so every feature generation receives a different `DataProviderResources`. `ContractBoardFeature` asks its manager for `MYSQL/system_data_rw` and never stores the global `DataProviderAPI`.

FeatureFramework therefore owns the infrastructure lifetime:

```text
start generation N
  create DataProvider scope → register SQL provider → publish ContractBoardApi

reload generation N
  stop new command/capability ingress → drain/cancel tasks → close feature data scope

start generation N+1
  read new config → create new scope/repository/cache/task → publish new API generation
```

Consumers should keep a `CapabilityRef<ContractBoardApi>` and resolve it for each operation. They must not retain a service instance or `DataSource` across that transition.

## Files worth reading in order

1. [`ContractBoardFeature.java`](ContractBoardFeature.java) — the subsystem composition root and ownership declaration.
2. [`ContractBoardService.java`](ContractBoardService.java) — async boundary, validation, cache policy, and shutdown rejection.
3. [`ContractRepository.java`](ContractRepository.java) — real SQL and the atomic claim rule.
4. [`ContractSnapshotCache.java`](ContractSnapshotCache.java) — hot versus last-known-good data.
5. [`ContractCommand.java`](ContractCommand.java) and [`ContractJoinListener.java`](ContractJoinListener.java) — platform adapters.
6. [`NetworkPlugin.java`](NetworkPlugin.java) — platform facade with a DataProvider contributor.

## What production code would add

For a paid reward, use a transaction/outbox or an idempotent fulfillment record instead of paying immediately after `claim()`. Add metrics for query latency, refresh age, claim conflicts, and cache fallback use. Put schema migrations in a versioned migration tool if your deployment process already has one.

Those additions belong here until another subsystem needs an independent lifecycle. FeatureFramework provides the boundary and ownership; the domain remains ordinary Java.
