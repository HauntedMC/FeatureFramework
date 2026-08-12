# 06 — Multi-feature Velocity plugin

A mature proxy plugin might contain:

```text
ServerDirectory
NetworkPlayers
Queue
Maintenance
Moderation
DiscordBridge
RedisSync
NetworkCommands
```

Do not put all of these behind one `ProxyManager`. Give each coherent subsystem a lifecycle boundary, then express only the relationships it needs.

A particularly useful pattern is to isolate infrastructure (`RedisSync`, external HTTP, Discord) behind capability providers. Domain features consume the contract and remain independent of the implementation.

`Features.java` shows centralized composition and `ProxyPlugin.java` shows a thin host bootstrap.
