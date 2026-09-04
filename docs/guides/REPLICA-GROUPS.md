# Static replica groups

FeatureFramework 2.0 can coordinate multiple copies of one Paper or Velocity application as a **static replica group**. The group has one manually configured leader and zero or more followers. This subsystem coordinates feature placement and replicated configuration; it does not elect nodes, discover platform identity, or provide automatic failover.

## Model

Applications supply a physical `ReplicaNodeIdentity` and a `ReplicaGroupIdentity(namespace, applicationId, groupId, configuredLeader)`. FeatureFramework deliberately has no DataRegistry dependency: an application may adapt DataRegistry identity, environment configuration, or another exact identity source into `ReplicaNodeIdentity`.

Only `nodeId == configuredLeader` is allowed to acquire authority. Followers never attempt acquisition. The DataProvider adapter uses the Redis resource key:

```text
ff:<namespace>:<applicationId>:<groupId>
```

and an owner of:

```text
<nodeId>/<bootUuid>
```

Normal leadership uses fenced `acquire()`, not authoritative `claim()`. The default lease is 15 seconds, renewed every 3 seconds, with a 2-second safety margin. The controller also measures local monotonic elapsed time since the last proven renewal; leader-only features are suppressed before the safe authority window can expire.

There is no automatic follower promotion in v1.

## Feature placement

Features default to:

```java
FeaturePlacement.ALL_NODES
```

A singleton feature or ingress component may declare:

```java
@FeatureDeclaration(
        name = "Ingress",
        version = "1.0.0",
        placement = FeaturePlacement.GROUP_LEADER_ONLY
)
```

Placement is checked before feature context creation, feature construction, resource allocation, or initialization. An enabled but ineligible feature appears as `FeatureState.SUPPRESSED` with structured `FeatureSuppression` detail rather than as a failure.

A required dependency from `ALL_NODES` to `GROUP_LEADER_ONLY` is rejected because followers could never satisfy it. Leader-only to all-node, leader-only to leader-only, and optional cross-placement dependencies are valid.

## Application bootstrap

Create and prepare the replica controller before building the host, then attach it before host startup:

```java
ReplicaController controller = ...;
controller.prepareBeforeHost();

PaperFeatureHost<MyPlugin, String> host = PaperFeatureHost
        .builder(plugin, MyPlugin.class, BuiltInFeatures.collection())
        .build();

controller.attach(host);
host.start();
controller.afterHostStarted();
```

`attach()` installs both the activation policy and the configuration mutation policy on the already constructed host. No separate host-bootstrap framework is required.

For leader-only work that is not a feature, register a `ReplicaLeaderService`. It starts only after host startup,
initial generation readiness, and fenced authority; it stops before leader-only feature reconciliation when authority
is lost:

```java
ReplicaLeaderServiceRegistration registration = controller.registerLeaderService(myIngress);
// Close registration before releasing the service's owning application state.
registration.close();
```

Close the controller during application shutdown after the host is no longer accepting application work. A held authority lease is released best-effort.

## Durable control plane

`featureframework-cluster-dataprovider` uses `RelationalDataAccess` directly. It does not use Hibernate and it never creates or alters schema at runtime.

Apply the shipped schema explicitly:

```text
featureframework-cluster-dataprovider/src/main/resources/schema/mysql-v1.sql
```

It defines:

- `ff_replica_group`
- `ff_config_generation`
- `ff_config_file`
- `ff_replica_node_state`

The repository validates the required schema-v1 tables and columns before an application enables replicated mode. The active group pointer stores both the active generation and its manifest hash; loading the pointer verifies that it still matches the immutable generation.

Every database key is scoped by `(namespace, application_id, group_id)`.

## Immutable generations and fencing

MySQL is the durable source of truth for configuration. Redis is used only for fenced authority.

Published generations are immutable and monotonically increasing. The current generation pointer never moves backwards. Rolling back generation 40 to the contents of generation 25 therefore publishes generation 41 with:

```text
generation = 41
source_generation = 25
```

A publisher must present a fencing token that is not older than the group’s highest accepted token. A process that loses authority cannot overwrite or reactivate configuration after a newer owner has been fenced in.

## First generation

For a new group with no active generation:

1. the configured leader acquires authority;
2. normal all-node features may start and generate their defaults;
3. leader-only activation remains suppressed while the group is uninitialized;
4. the host must start successfully;
5. the controller snapshots the managed configuration and publishes generation 1;
6. that generation becomes local LKG;
7. leader-only features are reconciled and may become active.

A follower connected to a healthy database that reports no active generation fails startup with a message directing the operator to start the configured leader first. A stale local LKG is **not** used to invent an initialized group.

## Existing-group startup and LKG

On normal startup, a follower downloads and verifies the active generation before host construction. A configured leader also loads the active generation; intentional local differences are treated as a startup candidate and the previous remote generation remains the rollback point until publication succeeds.

Local recovery data lives under:

```text
.replica/
  state.json
  generations/
  staging/
  drift/
```

Outage behavior is intentionally asymmetric:

- MySQL unavailable + verified compatible LKG: start from LKG.
- MySQL unavailable + no valid LKG: fail replicated startup.
- MySQL reachable + no active generation on a follower: fail as uninitialized; do not use LKG.
- Redis unavailable on a follower: ordinary features continue normally because followers do not need authority.
- Redis unavailable or authority unproven on the configured leader: all-node features continue, leader-only features are suppressed.

LKG files and manifests are hash-verified before use.

## Managed configuration

Framework defaults include only known configuration paths:

```text
config.yml
features/<Feature>/config.yml
features/<Feature>/messages.yml
features/<Feature>/<explicit language file>.yml
```

The application may extend `ManagedFileSet` with exact additional paths. `local/*.yml` is never recursively managed by default; secrets, keys, queues, runtime state, logs, dumps, and other node-local files must remain outside the replicated set.

On a follower, writes to managed paths are denied centrally through `ConfigMutationPolicy` with `ReplicaManagedConfigurationException`. This applies to file creation, normal YAML saves, reset-to-empty operations, and optional-file deletion, so individual features do not need cluster-aware write guards.

## Runtime synchronization and drift

Nodes poll the active generation (default about every 5 seconds); Redis pub/sub is not required. When a follower sees a newer compatible generation it:

1. downloads and verifies the manifest and file hashes;
2. stages the complete managed generation;
3. materializes it transactionally with rollback to the previous managed snapshot if any filesystem step fails;
4. reconciles `host.reloadGraph()`;
5. writes the new LKG only after success.

If host reconciliation rejects the new generation, the previous managed files and graph are restored and the node reports `OUT_OF_SYNC`.

If a follower’s managed files drift without a generation change, the controller backs the edited files up under `.replica/drift/` before restoring the authoritative generation. Operator edits are therefore not silently destroyed.

## Configuration compatibility

Applications supply a `ConfigCompatibility(applicationVersion, configCompatibilityVersion)`. Binary versions do not have to match as long as the configuration compatibility version does. For example:

```text
ProxyFeatures 5.0.0 -> config compatibility 1
ProxyFeatures 5.0.1 -> config compatibility 1
```

can participate in the same group during a rolling upgrade. A generation with an incompatible configuration version is not materialized and the node reports `OUT_OF_SYNC`.

## Backend module

Use `featureframework-cluster` for generic model/orchestration types and `featureframework-cluster-dataprovider` when the application uses DataProvider for MySQL + Redis. The backend opens a dedicated `featureframework.cluster` `DataProviderScope`, keeping its lifecycle isolated from feature-owned database registrations.
