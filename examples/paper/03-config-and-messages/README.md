# 03 — Configuration and messages

Feature defaults belong to the feature instead of a giant application-wide defaults class.

`ConfigurableWelcomeFeature.java` shows the two default-map hooks and an explicit reload decision.

Key ideas:

- `ConfigMap.put(...)` defines typed default values.
- `MessageMap.add(...)` defines stable localization keys and fallback text.
- `getConfigHandler()` is the feature's configuration boundary at runtime.
- `applyConfiguration()` decides whether an existing feature can absorb changes or should be recreated.

The safe default for stateful features is recreation. Only implement a soft application path when all affected state can be updated consistently.
