# 04 — Dependencies and integrations

`FeatureDefinitions.java` focuses on declaration-time relationships; the feature implementation classes themselves are omitted because they are not relevant to this example.

It demonstrates:

- `requiresFeatures` for a required named feature;
- `optionallyUsesFeatures` for optional behavior;
- `requiresPlugins` for an external Paper plugin;
- `startupOrder` as an ordering hint when there is no actual dependency.

If a consumer needs an interface rather than one particular feature, use a capability instead. That is the next example.
