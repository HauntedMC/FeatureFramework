# Shared HUD ownership

`featureframework-hud-api` supplies `HudService`, immutable `HudFrame` values and the platform-independent `HudCoordinator`. Adventure is provided by the platform. The API has no Bukkit entities, worlds, scheduler, SQL or static runtime singleton.

A host publishes `HudService` through its platform service boundary. ServerFeatures supplies the Paper host; other plugins acquire leases through the live service. Do not create a second shared coordinator when a host is already present. Producers must handle service replacement by closing old handles and acquiring new ones.

```java
var lease = hud.claim("MyPlugin:adventure", playerId, HudService.Channel.SIDEBAR,
        HudService.SESSION, Duration.ofSeconds(10));
lease.update(new HudFrame.Sidebar(Component.text("Adventure"), lines));
lease.renew(Duration.ofSeconds(10));
// On departure, release instead of restoring a captured old scoreboard:
lease.close();
```

Channels are sidebar, action bar, boss bar and title. Priorities are ambient (0), session (100), contextual (200), and critical (300). Equal priorities retain acquisition order. A newly acquired null frame suppresses underlying content without drawing. Frames must match the lease channel. Sidebars are bounded to 15 lines and managed boss-bar frames to two bars; progress is finite and clamped to 0..1. Frame collections, including boss-bar flags, are defensively copied.

All operations belong to the host owner thread. The coordinator accepts an injected thread check and monotonic clock. Hosts call `tick` to expire handles and request action-bar keepalive about once per second. Rendered equality suppresses unchanged writes. Title producers should use bounded lifetimes matching their display duration so stale titles do not reappear later.

Owner names are plugin names, optionally followed by colon-separated feature scopes. `releaseOwner("MyPlugin")` also releases `MyPlugin:feature` scopes. Hosts release scopes on plugin disable and player quit, and close the coordinator on shutdown. Handles are idempotently closeable; a stale handle cannot renew, update or remove a newer claim. Releasing the winning claim reveals the latest lower-priority frame, including changes made while hidden.

A renderer failure does not prevent other keys from being processed during a tick or shutdown. Errors are reported after that work. Hosts should isolate/throttle renderer error reporting. The renderer owns platform-side cleanup and must preserve unrelated teams/objectives.

The Paper action-bar toolkit also accepts an injected `Delivery` transport carrying priority. Normal cycle frames use ambient priority and manual overrides use critical priority; the default constructor preserves raw Paper delivery for hosts without shared HUDs.

Tests cover restoration of updated underlying content, suppression, expiry, stable ties, independent players, stale handles, keepalive/deduplication, failing renderers and a 100-player/10-run simulation.
