# 07 — Advanced Velocity lifecycle

Velocity does not have Bukkit's primary-thread lifecycle rule. Host lifecycle operations execute directly on their caller, so your application must choose where lifecycle mutations are initiated and coordinate concurrent work deliberately.

## Long-running I/O

Prefer feature-owned scheduling/resources where available. If a feature owns an external client or executor directly, stop accepting new work before closing it and release it in the feature lifecycle.

## Infrastructure capabilities

A useful advanced pattern is:

```text
RedisFeature -> provides NetworkBusApi
QueueFeature -> requires NetworkBusApi
ModerationFeature -> optionally uses NetworkBusApi
```

The domain features do not know which Redis library is used. Replacing the provider changes composition, not every consumer.

## Reload policy

Use soft reload only for state that can be replaced atomically. Recreate a feature when callbacks, subscriptions, network clients, or dependency topology would otherwise retain stale configuration.

## Verify withdrawal

During shutdown, services are withdrawn before the feature's domain state is released. Consumers must still avoid retaining implementation objects beyond their advertised lifecycle.
