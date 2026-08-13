# 03 — Configuration and messages

`ConfigurableWelcomeFeature.java` shows feature-scoped config defaults, message defaults, runtime config reads, and an explicit reload policy.

The important APIs are:

- `ConfigMap` for defaults;
- `MessageMap` for localization keys/default text;
- `getConfigHandler()` for effective runtime values;
- `applyConfiguration()` for the soft-reload vs recreation decision.

`RECREATE_REQUIRED` is a good default when changed configuration affects long-lived feature state.
