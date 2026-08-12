# Configuration, Localization, and Logging Toolkits

These cross-cutting systems are assembled into every managed feature context so features do not need global singletons for basic infrastructure.

## Configuration

`FeatureConfigHandler` is a feature-specific `ConfigView` backed by the framework configuration service. Feature defaults are supplied by `getDefaultConfig()` using `ConfigMap`.

Feature storage lives under feature-specific paths, and default injection can detect incompatible persisted value types according to the configured mismatch policy.

Use typed reads where possible:

```java
Boolean enabled = getConfigHandler().get("enabled", Boolean.class);
```

Global settings are available explicitly through the handler's global view methods; avoid silently coupling every feature to global configuration.

## Localization

Feature defaults come from `getDefaultMessages()` and `MessageMap`. Paper and Velocity assemble their own localization adapters while sharing the feature-level message model.

Keep stable keys and render messages at presentation boundaries. Domain services should return domain results, not preformatted chat strings.

## Logging

Each feature receives a feature logger through its context/base class. Use it for lifecycle and operational messages that should identify the responsible feature.

Good logs answer:

- which feature failed?
- during which lifecycle stage?
- which dependency/config/resource caused it?
- is the feature still usable or was startup aborted?

Avoid logging normal control flow at error level and avoid swallowing startup exceptions after logging them.

## Reload listeners

`FeatureConfigHandler` can register reload listeners, and managed cleanup clears those listeners before resource quiescing. Treat reload callbacks as feature-owned ingress just like platform listeners.

For complete reload policy guidance, read [Configuration and Localization](../guides/CONFIGURATION-AND-LOCALIZATION.md).
