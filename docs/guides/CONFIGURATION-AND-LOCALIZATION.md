# Configuration and Localization

Feature configuration and messages belong to the feature that uses them.

## Defaults

Override the default hooks when a feature needs configuration or messages:

```java
@Override
public ConfigMap getDefaultConfig() {
    return new ConfigMap()
            .put("enabled", true)
            .put("cooldown-seconds", 10);
}

@Override
public MessageMap getDefaultMessages() {
    MessageMap messages = new MessageMap();
    messages.add("cooldown", "<red>Please wait before using this again.</red>");
    return messages;
}
```

`FeatureConfigHandler` is a `ConfigView`, so read effective values directly through `getConfigHandler()`:

```java
Boolean enabled = getConfigHandler().get("enabled", Boolean.class);
```

Feature config is stored under `features/<feature>/config.yml`. Global settings are available explicitly through the handler's global view methods when a feature genuinely needs them.

## Reload behavior

The default `applyConfiguration()` result is `RECREATE_REQUIRED`. Keep that default unless a feature can apply changed values safely while it is running.

A soft update is a good fit for simple values such as limits or thresholds. Recreation is usually clearer when configuration changes affect listeners, commands, clients, subscriptions, dependency relationships, or other long-lived state.

## Localization

Define stable message keys with `MessageMap` and use the platform localization adapter when presenting text to a player or command sender.

Keep formatting at the presentation boundary. Domain services should return useful results or models rather than preformatted chat strings.

## Logging

Each managed feature has its own feature logger. Log lifecycle failures and operational problems with enough context to identify what failed, but avoid turning normal control flow into log noise.

A useful error should tell the developer which feature failed and what dependency, configuration value, or resource caused the failure. Do not catch a startup exception only to log it and continue with a feature that is not usable.

## Practical rules

- use stable, descriptive keys;
- provide defaults for optional settings;
- validate required values during initialization;
- document units and important ranges;
- keep secrets out of committed examples;
- keep one feature responsible for each feature-specific key.

See the [Paper](../../examples/paper/03-config-and-messages/README.md) and [Velocity](../../examples/velocity/03-config-and-messages/README.md) examples.
