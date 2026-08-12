# 02 — Feature-owned Paper resources

A managed feature should register resources through its feature scope whenever FeatureFramework supplies an adapter.

`ActivityFeature.java` demonstrates two common resources:

- a Bukkit listener registered by `FeatureListenerManager`;
- a repeating Bukkit task registered by `FeatureTaskManager`.

Both belong to the feature. When the feature is quiesced/cleaned up, the framework unregisters/cancels them. There is no matching manual cleanup code in `disable()`.

This ownership rule also applies to feature commands, caches, GUIs, data resources, and published services exposed by `PaperFeatureResources`.
