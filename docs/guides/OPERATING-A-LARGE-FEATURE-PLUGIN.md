# Operating a Large Feature Plugin

FeatureFramework is most valuable when a plugin is a small application platform: dozens of independently owned systems, optional infrastructure, live configuration changes, and a need to diagnose or reverse one subsystem without restarting the network.

The framework host is the control plane for that application. It serializes graph operations, keeps feature configuration in sync with those operations, and cleans feature-owned commands, listeners, tasks, services, caches, and data resources when a feature generation ends.

## Design a control plane as a feature

An admin command can itself be a normal `FeatureAdmin` feature. Register its Brigadier command through the feature resource manager so lifecycle ownership is visible and cleanup is automatic:

```java
@FeatureDeclaration(name = "FeatureAdmin", version = "1.0.0", enabledByDefault = true)
public final class FeatureAdminFeature extends PaperFeature<MyPlugin, Void> {
    public FeatureAdminFeature(PaperFeatureContext<MyPlugin, Void> context) {
        super(context);
    }

    @Override
    public void initialize() {
        resources().getCommandManager().registerBrigadierCommand(
                new FeatureAdminCommand(plugin()));
    }

    @Override public void disable() { }
}
```

Keep the command as a thin platform adapter. It should authorize, parse, suggest, and render; the host remains the authority that performs the operation. Both Paper and Velocity expose the same host operations:

```java
var host = plugin.featureHost();

host.enableFeature(featureName);      // persists enabled state and starts when prerequisites are already healthy
host.disableFeature(featureName);     // also stops loaded dependents when necessary
host.softReloadFeature(featureName);  // lets the feature apply config or asks the host to recreate it
host.reloadFeature(featureName);      // recreates the feature and its affected dependents
host.reload();                        // reloads host files and reconciles the complete configured graph
```

Use `FeatureCommandModel` for read-only `list`, `info`, and completion data rather than hand-maintaining a parallel registry. Use `FeatureOperationMessages` to translate the structured operation responses into your localization keys and placeholders. The framework provides the model and result taxonomy; your command owns permissions, audit logging, rate limits, confirmation policy, and message wording.

## Operations have deliberately different meanings

| Operation | Appropriate use | Important behavior |
|---|---|---|
| `enable <feature>` | Turn on a configured capability after its dependencies are healthy. | Persists enabled state and starts the feature when its prerequisites are already available; otherwise reports missing plugins/features rather than silently starting half a system. |
| `disable <feature>` | Remove a subsystem during an incident or planned maintenance. | Stops loaded dependents as required by the graph and cleans the feature's owned resources. |
| `softreload <feature>` | Apply a config change that a feature can safely mutate live. | Calls `applyConfiguration()`; a feature can return `RECREATE_REQUIRED`, in which case the host performs a safe recreation. |
| `reload <feature>` | Replace one feature generation after a config/client/integration change. | Recreates the feature and reloads affected dependents so they never keep a stale provider instance. |
| `reload` | Reconcile the entire plugin with edited configuration. | Reloads host config/localization, disables newly disabled features, reloads current features, then repeatedly enables configured features as their graph prerequisites become available. |

Avoid naming a raw file read `reload` in an operator-facing command. Reloading a feature config file without applying it can leave a running task, client, or cached policy constructed from old state. Reserve a `reloadlocal` or `refreshfiles` action for localization-only edits or for a documented feature-local refresh that has no lifecycle effect.

For example, a per-feature message refresh can be intentionally narrow:

```java
String key = host.managedHost().resolveFeatureKey(requestedName);
PaperFeature<Plugin, Void> feature = host.managedHost().registry().getLoadedFeature(key);
if (feature != null) {
    feature.getContext().localization().reloadLocalization();
}
```

Use a full feature reload when the changed setting affects a listener, task cadence, a remote client, a data subscription, or a published capability.

## Keep the control plane available

An admin feature may be disabled like any other feature. Decide this intentionally:

- Protect it from its own disable/reload subcommands, or put only the emergency command registration in the bootstrap.
- Treat graph-wide reload and forceful disables as elevated operations, separate from day-to-day config reload permission.
- Write an audit record before and after a mutation, including actor, requested feature, resolved feature key, response, and affected dependents.
- Surface `FeatureGraphReloadResult.stage()` and the failing feature in operator messages; do not reduce a failed graph reconcile to “reload failed”.

On Paper, invoke lifecycle operations from the server's primary-thread-safe path; `PaperFeatureHost` binds this automatically. On Velocity, operations run on the caller, so serialize commands with any surrounding administration workflow as appropriate. Read [Threading](../THREADING.md) before mixing an operation command with asynchronous database or HTTP work.

## Model integration boundaries, not implementation files

A network-sized graph often has this shape:

```text
NetworkIdentityFeature
  owns DataRegistry readiness and player references
  provides PlayerIdentityApi
       ├──> SessionPolicyFeature
       └──> SanctionGatewayFeature

NetworkBusFeature
  owns DataProvider Redis/database resources
  provides NetworkEventsApi
       ├──> CapacityFeature
       ├──> AnnouncementFeature
       └──> OperationsAuditFeature

FeatureAdmin
  reads the host graph and invokes lifecycle operations
```

The important boundary is the capability (`PlayerIdentityApi`, `NetworkEventsApi`), not a feature's repository, ORM context, or messaging client. Consumers can be recreated without retaining old raw handles; providers can change their implementation without changing every feature in the network.

## DataProvider and DataRegistry in a production feature

Use a custom host composition when the feature needs DataProvider or DataRegistry. The framework creates a `FeatureDataManager` per feature scope, so a feature's database connections, typed data access, Redis messaging provider, and ORM contexts end with that feature generation.

```text
initialize CapacityFeature
  -> resources().getDataManager().registerRedisMessagingProvider(...)
  -> build snapshot publisher/subscriber from the feature-owned provider
  -> expose CapacityApi only after the subscription is ready

reload CapacityFeature
  -> quiesce callbacks and remove the published capability
  -> close feature-owned data resources
  -> construct a fresh subscriber and capability from new configuration
```

DataRegistry is different: it supplies identity and readiness semantics. A Paper or Velocity DataRegistry feature should use the corresponding identity gate rather than block a connection event waiting for storage. The gate waits asynchronously, revalidates the player, and runs the action through the current feature generation's task manager. See the complete [DataProvider](../../examples/README.md#progression) and [DataRegistry](../../examples/README.md#progression) examples.

## Make complex features ordinary Java inside a clear lifetime

Do not turn every listener, command, repository, and policy into its own feature. A production feature can contain a rich internal design:

```text
CapacityFeature
├── CapacityConfig
├── CapacitySnapshotRepository
├── SnapshotPublisher
├── SnapshotSubscription
├── CapacityAdmissionPolicy
├── CapacityApi implementation
├── listener/
└── command/
```

One feature owns this cohesive subsystem because it has one meaningful availability and reload boundary. The listener, command, repository, and policies are normal, directly testable Java classes. Only promote another feature when it has an independent lifecycle, configuration, dependency set, or operational switch.

## Operational verification

For each feature that crosses the network boundary, test this sequence more than once:

```text
start graph -> exercise feature -> change config -> reload/recreate feature
-> verify commands/listeners/tasks/services/data subscriptions from generation N are gone
-> exercise generation N+1 -> disable provider -> verify consumers handle unavailability
```

This is where feature-scoped ownership pays for itself: correctness is not just whether startup works, but whether production changes leave precisely one active generation of every callback and integration.
