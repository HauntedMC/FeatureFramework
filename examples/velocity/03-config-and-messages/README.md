# 03 — Configuration and messages

Paper and Velocity use the same managed config/message contract.

`ConfigurableProxyFeature.java` defines defaults with `ConfigMap` and `MessageMap`, reads the effective feature config through `getConfigHandler()`, and chooses `RECREATE_REQUIRED` for config changes.

Keep Velocity-specific rendering at the presentation edge instead of returning formatted messages from domain code.
