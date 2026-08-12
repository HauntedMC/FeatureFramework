package nl.hauntedmc.featureframework.paper.time;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BukkitTimeTest {

    @Test
    void conversionFactoriesClampAndRoundAsExpected() {
        assertEquals(0L, BukkitTime.ticks(-5L).toTicks());
        assertEquals(1L, BukkitTime.milliseconds(1L).toTicks());
        assertEquals(2L, BukkitTime.milliseconds(51L).toTicks());
        assertEquals(40L, BukkitTime.seconds(2L).toTicks());
        assertEquals(20L, BukkitTime.seconds(1.0D).toTicks());
        assertEquals(1L, BukkitTime.seconds(0.001D).toTicks());
        assertEquals(1_200L, BukkitTime.minutes(1L).toTicks());
        assertEquals(72_000L, BukkitTime.hours(1L).toTicks());
    }

    @Test
    void largeMillisecondValuesRoundWithoutOverflow() {
        long expected = Long.MAX_VALUE / 50L + 1L;

        assertEquals(expected, BukkitTime.milliseconds(Long.MAX_VALUE).toTicks());
    }

    @Test
    void arithmeticOperationsHaveValueSemanticsAndUseExactMath() {
        assertEquals(BukkitTime.ticks(30L), BukkitTime.ticks(10L).plus(BukkitTime.ticks(20L)));
        assertEquals(BukkitTime.ticks(60L), BukkitTime.ticks(20L).multipliedBy(3L));
        assertEquals(BukkitTime.ticks(0L), BukkitTime.ticks(20L).multipliedBy(-1L));
        assertEquals("60t", BukkitTime.ticks(60L).toString());
    }

    @Test
    void invalidAndOverflowingValuesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> BukkitTime.seconds(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> BukkitTime.seconds(Double.POSITIVE_INFINITY));
        assertThrows(ArithmeticException.class, () -> BukkitTime.seconds(Long.MAX_VALUE));
        assertThrows(ArithmeticException.class,
                () -> BukkitTime.ticks(Long.MAX_VALUE).plus(BukkitTime.ticks(1L)));
        assertThrows(ArithmeticException.class,
                () -> BukkitTime.ticks(Long.MAX_VALUE).multipliedBy(2L));
    }
}
