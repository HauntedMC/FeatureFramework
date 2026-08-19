package nl.hauntedmc.featureframework.toolkit.token;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Generic, thread-safe token service.
 * - Tokens map to a payload that may be loaded asynchronously.
 * - Supports max uses, expiration, optional consume-on-empty.
 * - No external scheduler assumptions: caller supplies a loader that returns a completion stage.
 * <p>
 * Typical flow:
 * String token = service.create(() -> myAsyncLoader(), options);
 * {@code TokenResult<T> res = service.consume(token);}
 */
public final class TokenService<T> {

    private static final SecureRandom RNG = new SecureRandom();

    private final String namespace; // for debugging/metrics
    private final ConcurrentHashMap<String, Entry<T>> store = new ConcurrentHashMap<>();
    private volatile long lastCleanup = 0L;

    private static final long CLEANUP_INTERVAL_MILLIS = Duration.ofMinutes(2).toMillis();
    private static final int TOKEN_BYTES = 16; // 128-bit tokens

    public TokenService(String namespace) {
        this.namespace = Objects.requireNonNull(namespace, "namespace");
    }

    private static final class Entry<T> {
        final long expiresAt;                  // Long.MAX_VALUE means never
        final AtomicInteger usesLeft;          // -1 means infinite
        volatile boolean loading;
        volatile T payload;
        final boolean consumeOnEmpty;

        Entry(long expiresAt, int initialUses, boolean loading, boolean consumeOnEmpty) {
            this.expiresAt = expiresAt;
            this.usesLeft = new AtomicInteger(initialUses);
            this.loading = loading;
            this.consumeOnEmpty = consumeOnEmpty;
        }
    }

    /** Creates an unlimited token using the supplied asynchronous payload loader. */
    public String create(Supplier<? extends CompletionStage<T>> payloadLoader) {
        return create(payloadLoader, TokenOptions.infinite());
    }

    /**
     * Create a token and start loading the payload with the provided loader.
     * The loader should complete exceptionally or with null to represent "empty" payload.
     *
     * @param payloadLoader supplier returning a completion stage (can load sync or async)
     * @param options       token options (uses/expiry)
     */
    public String create(Supplier<? extends CompletionStage<T>> payloadLoader, TokenOptions options) {
        Objects.requireNonNull(payloadLoader, "payloadLoader");
        Objects.requireNonNull(options, "options");

        cleanupIfNeeded();

        final long now = System.currentTimeMillis();
        final long expiresAt = options.expireMillis() == Long.MAX_VALUE ? Long.MAX_VALUE : now + options.expireMillis();
        final int initialUses = options.maxUses() < 0 ? -1 : Math.max(0, options.maxUses());

        final String token = newToken();
        final Entry<T> entry = new Entry<>(expiresAt, initialUses, true, options.consumeOnEmpty());
        store.put(token, entry);

        CompletionStage<T> future;
        try {
            future = payloadLoader.get();
        } catch (Throwable t) {
            // loader threw before returning a stage => treat as empty
            entry.payload = null;
            entry.loading = false;
            return token;
        }

        if (future == null) {
            entry.payload = null;
            entry.loading = false;
            return token;
        }

        future.handle((res, err) -> {
            entry.payload = err != null ? null : res;
            entry.loading = false;
            return null;
        });

        return token;
    }

    /**
     * Consume one use (if finite) and return the current state.
     */
    public TokenResult<T> consume(String token) {
        if (token == null) return TokenResult.invalid();

        final Entry<T> e = store.get(token);
        if (e == null) return TokenResult.invalid();

        final long now = System.currentTimeMillis();
        if (now > e.expiresAt) {
            store.remove(token);
            return TokenResult.expired();
        }

        if (e.loading) {
            return TokenResult.loading();
        }

        final T p = e.payload;
        if (p == null) {
            if (e.consumeOnEmpty && e.usesLeft.get() > 0) {
                if (e.usesLeft.decrementAndGet() == 0) store.remove(token);
            }
            return TokenResult.empty();
        }

        // Uses accounting
        int uses = e.usesLeft.get();
        if (uses == 0) {
            store.remove(token);
            return TokenResult.expired();
        }
        if (uses > 0) {
            if (e.usesLeft.decrementAndGet() == 0) {
                store.remove(token);
            }
        }
        // uses == -1 => infinite

        return TokenResult.ok(p);
    }

    public void revoke(String token) {
        if (token != null) store.remove(token);
    }

    public void clear() {
        store.clear();
    }

    /** Removes expired or exhausted tokens immediately instead of waiting for opportunistic cleanup. */
    public void cleanup() {
        cleanup(System.currentTimeMillis());
        lastCleanup = System.currentTimeMillis();
    }

    public String namespace() {
        return namespace;
    }

    public int size() {
        return store.size();
    }

    public boolean isEmpty() {
        return store.isEmpty();
    }

    private String newToken() {
        final byte[] buf = new byte[TOKEN_BYTES];
        String token;
        do {
            RNG.nextBytes(buf);
            token = Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
        } while (store.containsKey(token));
        return token;
    }

    private void cleanupIfNeeded() {
        final long now = System.currentTimeMillis();
        if (now - lastCleanup < CLEANUP_INTERVAL_MILLIS && store.size() < 500) return;
        lastCleanup = now;
        cleanup(now);
    }

    private void cleanup(long now) {
        store.forEach((key, value) -> {
            if (now > value.expiresAt || value.usesLeft.get() == 0) {
                store.remove(key, value);
            }
        });
    }

    @Override
    public String toString() {
        return "TokenService{" + namespace + ", size=" + store.size() + '}';
    }
}
