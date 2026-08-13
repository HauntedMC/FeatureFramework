# 06 — Multi-feature Velocity plugin

This directory is a complete four-feature proxy application:

```text
ServerDirectoryFeature
  provides ServerDirectoryApi
       ├──> QueueFeature
       └──> NetworkCommandsFeature

MaintenanceFeature --optional relationship--> NetworkCommandsFeature
```

Every custom class shown in the graph exists here. `Features.java` is the application map and `ProxyPlugin.java` is a real Velocity bootstrap.

The example also shows two different kinds of relationship in one application: consumers require a capability when they need server-directory behavior, while `NetworkCommands` records `Maintenance` as an optional named-feature relationship.
