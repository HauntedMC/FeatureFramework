package nl.hauntedmc.featureframework.hud;

import java.time.Duration;
import java.util.UUID;

/** Owner-thread HUD arbitration. A null frame suppresses underlying content without drawing. */
public interface HudService {
    enum Channel { SIDEBAR, ACTION_BAR, BOSS_BAR, TITLE }
    int AMBIENT = 0;
    int SESSION = 100;
    int CONTEXT = 200;
    int CRITICAL = 300;

    /** Owner is the plugin name, optionally followed by colon-separated feature scopes. */
    Lease claim(String owner, UUID player, Channel channel, int priority, Duration lifetime);
    /** Releases this owner and its colon-separated descendant feature scopes. */
    void releaseOwner(String owner);
    void removePlayer(UUID player);

    interface Lease extends AutoCloseable {
        void update(HudFrame frame);
        void renew(Duration lifetime);
        boolean active();
        @Override void close();
    }
}
