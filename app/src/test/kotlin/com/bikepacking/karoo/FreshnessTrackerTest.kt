package com.bikepacking.karoo

import org.junit.Assert.*
import org.junit.Test

class FreshnessTrackerTest {

    @Test
    fun `HR fresh update now gives FRESH and value`() {
        val tracker = FreshnessTracker().apply {
            configure("hr", FreshnessConfigurations.HR)
        }
        val now = 1_000_000L
        tracker.touch("hr", now)

        assertEquals(DataFreshness.FRESH, tracker.getFreshness("hr", now))
        assertEquals(0L, tracker.getAgeMs("hr", now))
    }

    @Test
    fun `HR stale no update over 5 s gives STALE`() {
        val tracker = FreshnessTracker().apply {
            configure("hr", FreshnessConfigurations.HR)
        }
        tracker.touch("hr", 1_000_000L)

        assertEquals(DataFreshness.STALE, tracker.getFreshness("hr", 1_006_000L))
        assertEquals(6_000L, tracker.getAgeMs("hr", 1_006_000L))
    }

    @Test
    fun `HR missing no update over 12 s gives MISSING`() {
        val tracker = FreshnessTracker().apply {
            configure("hr", FreshnessConfigurations.HR)
        }
        tracker.touch("hr", 1_000_000L)

        assertEquals(DataFreshness.MISSING, tracker.getFreshness("hr", 1_013_000L))
        assertEquals(13_000L, tracker.getAgeMs("hr", 1_013_000L))
    }

    @Test
    fun `Power missing does not show last value`() {
        val tracker = FreshnessTracker().apply {
            configure("power", FreshnessConfigurations.POWER)
        }
        tracker.touch("power", 1_000_000L)

        assertEquals(DataFreshness.MISSING, tracker.getFreshness("power", 1_009_000L))
    }

    @Test
    fun `Wind missing gives neutral bg and display dash`() {
        val tracker = FreshnessTracker().apply {
            configure("wind", FreshnessConfigurations.WIND)
        }

        assertEquals(DataFreshness.MISSING, tracker.getFreshness("wind", 1_000_000L))
    }

    @Test
    fun `Grade missing is not treated as 0 percent`() {
        val tracker = FreshnessTracker().apply {
            configure("grade", FreshnessConfigurations.GRADE)
        }

        assertEquals(DataFreshness.MISSING, tracker.getFreshness("grade", 1_000_000L))
    }

    @Test
    fun `Logger records freshness and ageMs for FRESH`() {
        val tracker = FreshnessTracker().apply {
            configure("hr", FreshnessConfigurations.HR)
        }
        val now = 1_000_000L
        tracker.touch("hr", now - 1_200L)

        assertEquals(DataFreshness.FRESH, tracker.getFreshness("hr", now))
        assertEquals(1_200L, tracker.getAgeMs("hr", now))
    }

    @Test
    fun `Logger records freshness and ageMs for MISSING`() {
        val tracker = FreshnessTracker().apply {
            configure("hr", FreshnessConfigurations.HR)
        }
        val now = 1_000_000L

        assertEquals(DataFreshness.MISSING, tracker.getFreshness("hr", now))
        assertEquals(-1L, tracker.getAgeMs("hr", now))
    }

    @Test
    fun `Speed stale at 5s boundary`() {
        val tracker = FreshnessTracker().apply {
            configure("speed", FreshnessConfigurations.SPEED)
        }
        tracker.touch("speed", 1_000_000L)

        assertEquals(DataFreshness.STALE, tracker.getFreshness("speed", 1_005_000L))
    }

    @Test
    fun `Speed missing at 12s boundary`() {
        val tracker = FreshnessTracker().apply {
            configure("speed", FreshnessConfigurations.SPEED)
        }
        tracker.touch("speed", 1_000_000L)

        assertEquals(DataFreshness.MISSING, tracker.getFreshness("speed", 1_012_000L))
    }

    @Test
    fun `Cadence stale at 3s boundary`() {
        val tracker = FreshnessTracker().apply {
            configure("cadence", FreshnessConfigurations.CADENCE)
        }
        tracker.touch("cadence", 1_000_000L)

        assertEquals(DataFreshness.STALE, tracker.getFreshness("cadence", 1_003_000L))
    }

    @Test
    fun `Gear stale at 5s boundary`() {
        val tracker = FreshnessTracker().apply {
            configure("gear", FreshnessConfigurations.GEAR)
        }
        tracker.touch("gear", 1_000_000L)

        assertEquals(DataFreshness.STALE, tracker.getFreshness("gear", 1_005_000L))
    }

    @Test
    fun `Temp stale at 60s boundary`() {
        val tracker = FreshnessTracker().apply {
            configure("temp", FreshnessConfigurations.TEMP)
        }
        tracker.touch("temp", 1_000_000L)

        assertEquals(DataFreshness.STALE, tracker.getFreshness("temp", 1_060_000L))
    }

    @Test
    fun `Temp missing at 180s boundary`() {
        val tracker = FreshnessTracker().apply {
            configure("temp", FreshnessConfigurations.TEMP)
        }
        tracker.touch("temp", 1_000_000L)

        assertEquals(DataFreshness.MISSING, tracker.getFreshness("temp", 1_180_000L))
    }

    @Test
    fun `Wind stale at 20s boundary`() {
        val tracker = FreshnessTracker().apply {
            configure("wind", FreshnessConfigurations.WIND)
        }
        tracker.touch("wind", 1_000_000L)

        assertEquals(DataFreshness.STALE, tracker.getFreshness("wind", 1_020_000L))
    }

    @Test
    fun `Wind missing at 60s boundary`() {
        val tracker = FreshnessTracker().apply {
            configure("wind", FreshnessConfigurations.WIND)
        }
        tracker.touch("wind", 1_000_000L)

        assertEquals(DataFreshness.MISSING, tracker.getFreshness("wind", 1_060_000L))
    }

    @Test
    fun `Reset clears all tracked data`() {
        val tracker = FreshnessTracker().apply {
            configure("hr", FreshnessConfigurations.HR)
            configure("power", FreshnessConfigurations.POWER)
        }
        tracker.touch("hr", 1_000_000L)
        tracker.touch("power", 1_000_000L)

        tracker.reset()

        assertEquals(DataFreshness.MISSING, tracker.getFreshness("hr", 1_000_000L))
        assertEquals(DataFreshness.MISSING, tracker.getFreshness("power", 1_000_000L))
    }

    @Test
    fun `Unconfigured key returns MISSING`() {
        val tracker = FreshnessTracker()

        assertEquals(DataFreshness.MISSING, tracker.getFreshness("unknown", 1_000_000L))
    }
}
