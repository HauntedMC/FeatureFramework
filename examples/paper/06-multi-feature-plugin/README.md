# 06 — Multi-feature Paper plugin

This example shows the shape of a larger application:

```text
Profiles
├──> Chat
└──> Moderation

PlaceholderBridge -> requires PlaceholderAPI
```

`Features.java` keeps the application graph in one place. `MyPlugin.java` stays focused on creating and starting the host.

The feature classes referenced by the composition example represent normal application features and are intentionally not repeated here. In a real plugin, each feature package can contain its own repositories, services, listeners, commands, handlers, and models.
