# 03 — Configuration and messages

Files:

- `MyPlugin.java` — normal ready-to-use Paper host.
- `ConfigurableWelcomeFeature.java` — config defaults, message defaults, runtime reads, and reload policy.

`defaultConfig()` and `defaultMessages()` describe the defaults owned by this feature. At runtime, read effective values through `config()` rather than opening YAML yourself.

`applyConfiguration()` answers a separate question: can the **existing feature instance** safely absorb changed config? This example returns `RECREATE_REQUIRED`, so the host recreates the feature instead of keeping callbacks/state that may have been built from old values.

That is a good conservative default. Use a soft apply path only when all affected state can be updated consistently.
