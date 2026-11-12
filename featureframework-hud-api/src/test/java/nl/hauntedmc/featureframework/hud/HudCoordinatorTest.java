package nl.hauntedmc.featureframework.hud;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;

class HudCoordinatorTest {
    private final UUID player = UUID.randomUUID();
    private final AtomicLong clock = new AtomicLong();
    private final List<HudFrame> writes = new ArrayList<>();
    private final HudCoordinator hud = new HudCoordinator((id, channel, frame) -> writes.add(frame), clock::get, () -> {});
    private final Duration lifetime = Duration.ofSeconds(10);
    private HudFrame.ActionBar text(String value) { return new HudFrame.ActionBar(Component.text(value)); }

    @Test void departureRestoresLatestUnderlyingContent() {
        var ambient = hud.claim("server", player, HudService.Channel.ACTION_BAR, 0, lifetime);
        ambient.update(text("before"));
        var dungeon = hud.claim("dungeon", player, HudService.Channel.ACTION_BAR, 100, lifetime);
        dungeon.update(text("in run"));
        ambient.update(text("updated while hidden"));
        assertEquals(text("in run"), writes.getLast());
        dungeon.close();
        assertEquals(text("updated while hidden"), writes.getLast());
        dungeon.close();
        assertTrue(ambient.active());
    }

    @Test void emptyClaimSuppressesAndExpiryRevealsAmbient() {
        var ambient = hud.claim("server", player, HudService.Channel.ACTION_BAR, 0, lifetime);
        ambient.update(text("ambient"));
        hud.claim("run", player, HudService.Channel.ACTION_BAR, 100, Duration.ofSeconds(2));
        assertNull(writes.getLast());
        clock.set(Duration.ofSeconds(3).toNanos());
        hud.tick(false);
        assertEquals(text("ambient"), writes.getLast());
    }

    @Test void priorityAndStableTiesAreIndependentForEachPlayer() {
        var first = hud.claim("first", player, HudService.Channel.ACTION_BAR, 200, lifetime);
        first.update(text("first"));
        var second = hud.claim("second", player, HudService.Channel.ACTION_BAR, 200, lifetime);
        second.update(text("second"));
        assertEquals(text("first"), writes.getLast());
        var critical = hud.claim("critical", player, HudService.Channel.ACTION_BAR, 300, lifetime);
        critical.update(text("urgent"));
        assertEquals(text("urgent"), writes.getLast());
        hud.releaseOwner("critical");
        assertEquals(text("first"), writes.getLast());
        first.close();
        assertEquals(text("second"), writes.getLast());
        hud.removePlayer(UUID.randomUUID());
        assertTrue(second.active());
    }

    @Test void staleHandlesCannotRenewAndUnchangedFramesAreDeduplicated() {
        var lease = hud.claim("run", player, HudService.Channel.ACTION_BAR, 100, lifetime);
        lease.update(text("same"));
        int count = writes.size();
        lease.update(text("same"));
        hud.tick(false);
        assertEquals(count, writes.size());
        hud.tick(true);
        assertEquals(count + 1, writes.size());
        hud.releaseOwner("run");
        assertThrows(IllegalStateException.class, () -> lease.renew(lifetime));
        lease.close();
        hud.close();
        lease.close();
    }

    @Test void aHundredPlayersRestoreIndependentlyWithoutUnchangedSidebarWrites() {
        var frames=new java.util.HashMap<UUID,HudFrame>();
        var counter=new java.util.concurrent.atomic.AtomicInteger();
        var coordinator=new HudCoordinator((id,channel,frame) -> { frames.put(id,frame); counter.incrementAndGet(); },clock::get,() -> {});
        var participants=new java.util.LinkedHashMap<UUID,HudService.Lease>();
        for(int i=0;i<100;i++) {
            UUID id=UUID.randomUUID();
            var ambient=coordinator.claim("server",id,HudService.Channel.SIDEBAR,0,lifetime);
            ambient.update(new HudFrame.Sidebar(Component.text("Server"),List.of(Component.text("latest"))));
            var run=coordinator.claim("run-"+(i/10),id,HudService.Channel.SIDEBAR,100,lifetime);
            run.update(new HudFrame.Sidebar(Component.text("Dungeon"),List.of(Component.text("running"))));
            participants.put(id,run);
        }
        int initial=counter.get();
        for(int i=0;i<200;i++) coordinator.tick(false);
        assertEquals(initial,counter.get(),"Unchanged HUDs must not write packets");
        coordinator.releaseOwner("run-0");
        assertEquals(initial+10,counter.get());
        assertEquals(90,participants.values().stream().filter(HudService.Lease::active).count());
        coordinator.close();
        assertTrue(participants.values().stream().noneMatch(HudService.Lease::active));
    }

    @Test void oneBrokenRendererCannotPreventOtherPlayersOrFinalCleanup() {
        UUID broken=UUID.randomUUID(),healthy=UUID.randomUUID();
        var fail=new java.util.concurrent.atomic.AtomicBoolean();
        var frames=new java.util.HashMap<UUID,HudFrame>();
        var coordinator=new HudCoordinator((id,channel,frame) -> {
            if(id.equals(broken) && fail.get()) throw new IllegalStateException("disconnected renderer");
            frames.put(id,frame);
        },clock::get,() -> {});
        var brokenLease=coordinator.claim("run",broken,HudService.Channel.ACTION_BAR,100,lifetime);
        var healthyLease=coordinator.claim("run",healthy,HudService.Channel.ACTION_BAR,100,lifetime);
        brokenLease.update(text("broken")); healthyLease.update(text("healthy"));
        fail.set(true);
        assertThrows(IllegalStateException.class,() -> coordinator.tick(true));
        assertEquals(text("healthy"),frames.get(healthy));
        assertThrows(IllegalStateException.class,coordinator::close);
        assertFalse(brokenLease.active()); assertFalse(healthyLease.active());
        assertNull(frames.get(healthy));
    }
    @Test void failedOwnerCleanupStillReleasesAllPlayersAndDescendantScopes() {
        var fail = new java.util.concurrent.atomic.AtomicBoolean();
        var coordinator = new HudCoordinator((id, channel, frame) -> {
            if (fail.get()) throw new IllegalStateException("renderer failed");
        }, clock::get, () -> {});
        var first = coordinator.claim("run", player, HudService.Channel.ACTION_BAR, 100, lifetime);
        var second = coordinator.claim("run:preview", UUID.randomUUID(), HudService.Channel.ACTION_BAR, 100, lifetime);
        first.update(text("one")); second.update(text("two"));
        fail.set(true);
        assertThrows(IllegalStateException.class, () -> coordinator.releaseOwner("run"));
        assertFalse(first.active()); assertFalse(second.active());
    }

    @Test void failedClaimDoesNotLeaveAnInvisibleLeaseSuppressingAmbientContent() {
        var fail = new java.util.concurrent.atomic.AtomicBoolean();
        var frames = new ArrayList<HudFrame>();
        var coordinator = new HudCoordinator((id, channel, frame) -> {
            if (fail.get() && frame == null) throw new IllegalStateException("suppression failed");
            frames.add(frame);
        }, clock::get, () -> {});
        var ambient = coordinator.claim("server", player, HudService.Channel.ACTION_BAR, 0, lifetime);
        ambient.update(text("before"));
        fail.set(true);
        assertThrows(IllegalStateException.class, () -> coordinator.claim("run", player, HudService.Channel.ACTION_BAR, 100, lifetime));
        ambient.update(text("after"));
        assertEquals(text("after"), frames.getLast());
    }

    @Test void failedPlayerCleanupStillInvalidatesEveryChannel() {
        var fail = new java.util.concurrent.atomic.AtomicBoolean();
        var coordinator = new HudCoordinator((id, channel, frame) -> {
            if (fail.get()) throw new IllegalStateException("renderer failed");
        }, clock::get, () -> {});
        var action = coordinator.claim("run", player, HudService.Channel.ACTION_BAR, 100, lifetime);
        var title = coordinator.claim("run", player, HudService.Channel.TITLE, 100, lifetime);
        fail.set(true);
        assertThrows(IllegalStateException.class, () -> coordinator.removePlayer(player));
        assertFalse(action.active()); assertFalse(title.active());
    }
}
