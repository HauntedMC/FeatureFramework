package nl.hauntedmc.featureframework.hud;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.LongSupplier;

/** Platform-independent lease engine. The host drives expiry/keepalive and enforces its thread. */
public final class HudCoordinator implements HudService, AutoCloseable {
    @FunctionalInterface
    public interface Renderer { void render(UUID player, Channel channel, HudFrame frame); }
    private record Key(UUID player, Channel channel) {}
    private final Map<Key, Map<Long, Claim>> claims = new HashMap<>();
    private final Map<Key, HudFrame> rendered = new HashMap<>();
    private final Renderer renderer;
    private final LongSupplier clock;
    private final Runnable threadCheck;
    private long sequence;
    private boolean closed;

    public HudCoordinator(Renderer renderer, LongSupplier clock, Runnable threadCheck) {
        this.renderer = Objects.requireNonNull(renderer);
        this.clock = Objects.requireNonNull(clock);
        this.threadCheck = Objects.requireNonNull(threadCheck);
    }

    @Override public Lease claim(String owner, UUID player, Channel channel, int priority, Duration lifetime) {
        check();
        if (owner == null || owner.isBlank()) throw new IllegalArgumentException("Missing HUD owner");
        Key key = new Key(Objects.requireNonNull(player), Objects.requireNonNull(channel));
        Claim claim = new Claim(owner, key, priority, ++sequence, deadline(lifetime));
        claims.computeIfAbsent(key, ignored -> new LinkedHashMap<>()).put(claim.id, claim);
        try { render(key, false); }
        catch (RuntimeException failure) {
            claims.get(key).remove(claim.id);
            try { render(key, false); }
            catch (RuntimeException restoreFailure) { if (restoreFailure != failure) failure.addSuppressed(restoreFailure); }
            throw failure;
        }
        return claim;
    }

    /** Expire leases every host tick; request action-bar keepalive approximately once a second. */
    public void tick(boolean keepalive) {
        check();
        RuntimeException failure=null;
        for (Key key : new ArrayList<>(claims.keySet())) {
            claims.get(key).values().removeIf(claim -> claim.expires <= clock.getAsLong());
            try { render(key, keepalive && key.channel == Channel.ACTION_BAR); }
            catch(RuntimeException problem) { if(failure==null) failure=problem; else if(problem!=failure) failure.addSuppressed(problem); }
        }
        if(failure!=null) throw failure;
    }

    @Override public void releaseOwner(String owner) {
        check();
        Objects.requireNonNull(owner);
        RuntimeException failure = null;
        for (Key key : new ArrayList<>(claims.keySet())) {
            claims.get(key).values().removeIf(claim -> (claim.owner.equals(owner) || claim.owner.startsWith(owner + ":")));
            try { render(key, false); }
            catch (RuntimeException problem) { if (failure == null) failure = problem; else if (problem != failure) failure.addSuppressed(problem); }
        }
        if (failure != null) throw failure;
    }

    @Override public void removePlayer(UUID player) {
        check();
        Objects.requireNonNull(player);
        RuntimeException failure = null;
        for (Key key : new ArrayList<>(claims.keySet())) if (key.player.equals(player)) {
            claims.remove(key);
            rendered.remove(key);
            try { renderer.render(player, key.channel, null); }
            catch (RuntimeException problem) { if (failure == null) failure = problem; else if (problem != failure) failure.addSuppressed(problem); }
        }
        if (failure != null) throw failure;
    }

    private void render(Key key, boolean force) {
        Map<Long, Claim> entries = claims.get(key);
        Claim winner = entries == null ? null : entries.values().stream()
                .filter(claim -> claim.expires > clock.getAsLong())
                .min(Comparator.<Claim>comparingInt(claim -> claim.priority).reversed()
                        .thenComparingLong(claim -> claim.id)).orElse(null);
        HudFrame frame = winner == null ? null : winner.frame;
        if (force || !Objects.equals(frame, rendered.get(key))) {
            renderer.render(key.player, key.channel, frame);
            if (frame == null) rendered.remove(key); else rendered.put(key, frame);
        }
        if (entries != null && entries.isEmpty()) claims.remove(key);
    }

    private long deadline(Duration lifetime) {
        if (lifetime == null || lifetime.isNegative() || lifetime.isZero())
            throw new IllegalArgumentException("Positive HUD lease duration required");
        return Math.addExact(clock.getAsLong(), lifetime.toNanos());
    }
    private void check() {
        threadCheck.run();
        if (closed) throw new IllegalStateException("HUD coordinator is closed");
    }
    @Override public void close() {
        if (closed) return;
        threadCheck.run();
        RuntimeException failure=null;
        for (Key key : new ArrayList<>(claims.keySet())) {
            try { renderer.render(key.player, key.channel, null); }
            catch(RuntimeException problem) { if(failure==null) failure=problem; else if(problem!=failure) failure.addSuppressed(problem); }
        }
        claims.clear(); rendered.clear(); closed = true;
        if(failure!=null) throw failure;
    }

    private final class Claim implements Lease {
        private final String owner;
        private final Key key;
        private final int priority;
        private final long id;
        private long expires;
        private HudFrame frame;
        private Claim(String owner, Key key, int priority, long id, long expires) {
            this.owner = owner; this.key = key; this.priority = priority; this.id = id; this.expires = expires;
        }
        public boolean active() {
            threadCheck.run();
            return !closed && expires > clock.getAsLong()
                    && claims.getOrDefault(key, Map.of()).get(id) == this;
        }
        public void update(HudFrame next) {
            check();
            if (!active()) throw new IllegalStateException("HUD lease expired");
            if (next != null && next.channel() != key.channel) throw new IllegalArgumentException("Wrong HUD channel");
            frame = next;
            render(key, false);
        }
        public void renew(Duration lifetime) {
            check();
            if (!active()) throw new IllegalStateException("HUD lease expired");
            expires = deadline(lifetime);
        }
        public void close() {
            threadCheck.run();
            if (closed) return;
            Map<Long, Claim> entries = claims.get(key);
            if (entries != null && entries.remove(id, this)) render(key, false);
        }
    }
}
