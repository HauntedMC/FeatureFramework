# 03 — Configuration and messages

Paper and Velocity share the managed feature config/message contract, so feature defaults look almost identical across platforms.

`ConfigurableProxyFeature.java` demonstrates `ConfigMap`, `MessageMap`, and the conservative `RECREATE_REQUIRED` reload policy.

Keep proxy-specific rendering in the Velocity localization layer; keep domain decisions independent of formatted message strings.
