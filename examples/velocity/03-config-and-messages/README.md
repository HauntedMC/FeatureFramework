# 03 — Configuration and messages

This directory contains both the real Velocity bootstrap and the configurable feature.

`ConfigurableProxyFeature`:

- defines typed defaults with `ConfigMap`;
- defines message keys/default text with `MessageMap`;
- reads effective values through `getConfigHandler()`;
- returns `RECREATE_REQUIRED` for configuration changes.

Paper and Velocity intentionally share this managed config/message contract. Only the platform presentation/localization adapter differs.
