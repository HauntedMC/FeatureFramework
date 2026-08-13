# 02 — Feature-owned Paper resources

`ActivityFeature.java` registers a Bukkit listener and a repeating task through `PaperFeatureResources`.

Both resources belong to the feature, so the framework unregisters/cancels them when the feature stops. There is no matching manual cleanup in `disable()`.

The same ownership idea applies to feature commands, caches, GUIs, data resources, and published services.
