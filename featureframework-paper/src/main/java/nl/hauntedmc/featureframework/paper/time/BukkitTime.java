package nl.hauntedmc.featureframework.paper.time;

import java.util.Objects;

/**
 * Immutable time quantity expressed in Minecraft ticks (20 ticks = 1 second).
 * Use factories like {@link #seconds(long)}, {@link #milliseconds(long)}, or {@link #ticks(long)}.
 * Conversion:
 * - milliseconds -> ticks uses ceiling ((ms + 49) / 50) so short delays aren't lost.
 */
public final class BukkitTime {
    private static final BukkitTime ZERO = new BukkitTime(0L);

    private final long ticks;

    private BukkitTime(long ticks) {
        this.ticks = ticks;
    }

    /**
     * Create a Time from raw ticks.
     */
    public static BukkitTime ticks(long ticks) {
        return ticks <= 0L ? ZERO : new BukkitTime(ticks);
    }

    /**
     * Create a Time from milliseconds (ceil to the next tick).
     */
    public static BukkitTime milliseconds(long millis) {
        if (millis <= 0L) {
            return ZERO;
        }
        long wholeTicks = millis / 50L;
        long roundedTicks = millis % 50L == 0L ? wholeTicks : Math.addExact(wholeTicks, 1L);
        return new BukkitTime(roundedTicks);
    }

    /**
     * Create a Time from whole seconds.
     */
    public static BukkitTime seconds(long seconds) {
        if (seconds <= 0L) {
            return ZERO;
        }
        return new BukkitTime(Math.multiplyExact(seconds, 20L));
    }

    /**
     * Create a Time from fractional seconds (ceil to the next millisecond).
     */
    public static BukkitTime seconds(double seconds) {
        if (!Double.isFinite(seconds)) {
            throw new IllegalArgumentException("seconds must be finite");
        }
        if (seconds <= 0.0D) {
            return ZERO;
        }
        double ticks = seconds * 20.0D;
        if (ticks > Long.MAX_VALUE) {
            throw new ArithmeticException("seconds exceed the supported tick range");
        }
        return new BukkitTime((long) Math.ceil(ticks));
    }

    /**
     * Create a Time from minutes.
     */
    public static BukkitTime minutes(long minutes) {
        if (minutes <= 0L) {
            return ZERO;
        }
        return seconds(Math.multiplyExact(minutes, 60L));
    }

    /**
     * Create a Time from hours.
     */
    public static BukkitTime hours(long hours) {
        if (hours <= 0L) {
            return ZERO;
        }
        return seconds(Math.multiplyExact(hours, 3600L));
    }

    /**
     * Number of ticks represented by this Time.
     */
    public long toTicks() {
        return ticks;
    }

    /**
     * Add two Time values (throws on overflow).
     */
    public BukkitTime plus(BukkitTime other) {
        return new BukkitTime(Math.addExact(this.ticks, Objects.requireNonNull(other, "other").ticks));
    }

    /**
     * Multiply a Time by a factor (throws on overflow).
     */
    public BukkitTime multipliedBy(long factor) {
        if (factor <= 0L || ticks == 0L) {
            return ZERO;
        }
        return new BukkitTime(Math.multiplyExact(this.ticks, factor));
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof BukkitTime time && ticks == time.ticks;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(ticks);
    }

    @Override
    public String toString() {
        return ticks + "t";
    }
}
