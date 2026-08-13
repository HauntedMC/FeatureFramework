# 06 — Multi-feature Velocity plugin

The example composition contains four features:

```text
ServerDirectory
├──> Queue
└──> NetworkCommands

Maintenance --optional--> NetworkCommands
```

`Features.java` keeps these relationships in one place and `ProxyPlugin.java` only creates and starts the host.

In a larger network plugin, infrastructure such as Redis, Discord, or external HTTP integrations often works well as separate capability providers. Domain features then depend on the contract instead of the implementation.
