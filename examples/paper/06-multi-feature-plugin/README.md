# 06 — Multi-feature Paper plugin

This is the recommended shape for a larger plugin.

```text
MyPlugin
  -> Features.all()
      -> Profiles
      -> Chat        requires Profiles
      -> Moderation  requires Profiles
      -> PlaceholderBridge requires PlaceholderAPI
      -> Cosmetics   optional economy capability
```

Keep domain code inside feature packages. Keep the bootstrap focused on host creation. Keep definitions in one composition layer so a reviewer can understand the application graph without searching constructors.

`Features.java` demonstrates centralized composition. `MyPlugin.java` demonstrates the final thin bootstrap.

For a production application, individual feature packages can contain normal repositories, services, handlers, listeners, commands, and DTOs. They do not all need to become framework types.
