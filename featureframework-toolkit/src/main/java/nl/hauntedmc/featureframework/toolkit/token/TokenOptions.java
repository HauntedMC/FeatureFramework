package nl.hauntedmc.featureframework.toolkit.token;

import java.time.Duration;

/**
 * Options for token creation.
 * - maxUses: -1 for infinite
 * - expireAfter: null or negative duration => never expires
 * - consumeOnEmpty: if true, consumes a use when payload resolves to null/empty
 */
public final class TokenOptions {

    private final int maxUses;
    private final long expireMillis; // Long.MAX_VALUE means never expires
    private final boolean consumeOnEmpty;

    private TokenOptions(int maxUses, long expireMillis, boolean consumeOnEmpty) {
        this.maxUses = maxUses;
        this.expireMillis = expireMillis;
        this.consumeOnEmpty = consumeOnEmpty;
    }

    public static Builder builder() {
        return new Builder();
    }

    public int maxUses() {
        return maxUses;
    }

    public long expireMillis() {
        return expireMillis;
    }

    public boolean consumeOnEmpty() {
        return consumeOnEmpty;
    }

    public static final class Builder {
        private int maxUses = -1;
        private long expireMillis = Long.MAX_VALUE;
        private boolean consumeOnEmpty = true;

        /**
         * Set maximum uses; -1 for infinite.
         */
        public Builder maxUses(int maxUses) {
            this.maxUses = maxUses;
            return this;
        }

        /**
         * Set expiration; negative or null => never expires.
         */
        public Builder expireAfter(Duration duration) {
            if (duration == null || duration.isNegative()) {
                this.expireMillis = Long.MAX_VALUE;
            } else {
                this.expireMillis = Math.max(1L, duration.toMillis());
            }
            return this;
        }

        /**
         * If true, consuming a token with empty payload decrements uses (prevents spam).
         */
        public Builder consumeOnEmpty(boolean consumeOnEmpty) {
            this.consumeOnEmpty = consumeOnEmpty;
            return this;
        }

        public TokenOptions build() {
            return new TokenOptions(maxUses, expireMillis, consumeOnEmpty);
        }
    }

    public static TokenOptions of(int maxUses, Duration expireAfter, boolean consumeOnEmpty) {
        return builder().maxUses(maxUses).expireAfter(expireAfter).consumeOnEmpty(consumeOnEmpty).build();
    }

    public static TokenOptions infinite() {
        return new TokenOptions(-1, Long.MAX_VALUE, true);
    }
}
