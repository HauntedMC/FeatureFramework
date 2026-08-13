# 07 — Advanced Velocity lifecycle

Velocity does not use Bukkit's primary-thread lifecycle model. Feature host operations execute on their caller, so coordinate concurrent lifecycle operations and shared state like normal proxy code.

## Long-running work

Prefer feature-owned scheduling where it fits. If a feature owns an external client, executor, or subscription directly, stop new work and close it during feature shutdown.

## Infrastructure capabilities

A useful pattern is:

```text
RedisFeature -> provides NetworkBusApi
QueueFeature -> requires NetworkBusApi
ModerationFeature -> optionally uses NetworkBusApi
```

The consumers only know the contract, so changing the Redis client or provider implementation does not require changing them.

## Reload policy

Use a soft config update only when the relevant state can change safely in place. Recreate the feature when subscriptions, network clients, callbacks, or dependencies would otherwise keep old state.

Services are withdrawn as their provider shuts down, so consumers should not retain provider implementation objects beyond the feature lifetime.
