# FeatureFramework examples

The examples are split by platform and increase in complexity:

- [Paper](paper/README.md)
- [Velocity](velocity/README.md)

## How to use these examples

**Every code example directory is independent.** It contains every application class, feature class, capability contract, and helper that its source code references. You do not need to copy classes from an earlier example. Example 10 is a design reference that points back to the concrete DataProvider and DataRegistry compositions in examples 08–09. Example 11 combines those building blocks in an end-to-end feature with domain logic and explicit failure handling.

Framework/platform classes still come from the real dependencies. Add FeatureFramework to a normal Paper or Velocity project using the setup in the [root README](../README.md), then copy the example that is closest to what you are building.

The examples leave out build files and platform metadata so their source can be dropped into an existing plugin project without repeating Maven boilerplate.

## Progression

Both platform tracks cover:

1. one managed feature and host bootstrap;
2. feature-owned listeners and tasks;
3. configuration and messages;
4. required/optional features and external plugins;
5. capability provider/consumer design;
6. a larger multi-feature application;
7. lifecycle, async work, and recreation;
8. DataProvider with feature-owned data resources;
9. DataRegistry and player identity readiness.
10. an operations/control-plane design for a live, multi-feature network plugin.
11. an end-to-end subsystem with DataProvider, caching, config, messages, tasks, commands/listeners, and a capability.

All examples use the ready-to-use platform facade. Examples 8–9 add DataProvider and DataRegistry through declaration-driven resource contributors.

Example 10 is a control-plane reference: it connects the host's structured lifecycle operations, feature-owned command registration, config/localization reload semantics, DataProvider ownership, and capability boundaries. It complements examples 8–9, where those optional integrations are shown directly.

Example 11 differs by platform on purpose:

| Platform | End-to-end subsystem | Critical design problem |
|---|---|---|
| Paper | [Persistent ContractBoard](paper/11-persistent-contract-board/README.md) | transactional SQL mutations, async main-thread handoff, hot versus last-known caches |
| Velocity | [Adaptive rollout router](velocity/11-adaptive-rollout-router/README.md) | real-time Redis snapshots, cross-thread handoff, freshness-bounded synchronous routing |

The Paper and Velocity examples focus on different runtime concerns, so read both when designing features that span a network.
