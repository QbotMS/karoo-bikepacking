package com.bikepacking.karoo.field

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClimbContextProviderTest {

    // 7.5%, 5.0%, 2.0%
    private val climbs = listOf(
        RawClimbData(startDistanceM = 5000.0, lengthM = 800.0, elevationM = 60.0),   // 7.5%
        RawClimbData(startDistanceM = 12000.0, lengthM = 2000.0, elevationM = 100.0), // 5.0%
        RawClimbData(startDistanceM = 20000.0, lengthM = 500.0, elevationM = 10.0),   // 2.0%
    )

    // ── empty ──────────────────────────────────────────────

    @Test
    fun `no climbs returns empty context`() {
        val ctx = ClimbContextProvider.computeContext(emptyList(), progressM = 0.0)
        assertEquals(0, ctx.climbCount)
        assertNull(ctx.activeClimb)
        assertNull(ctx.nextClimb)
        assertNull(ctx.lastCompletedClimb)
        assertFalse(ctx.isOnClimb)
        assertFalse(ctx.hasClimbAhead)
        assertNull(ctx.distanceToNextClimbStartM)
        assertNull(ctx.distanceToActiveClimbEndM)
        assertEquals(ClimbDifficulty.UNKNOWN, ctx.difficultyBucket)
    }

    // ── before first climb ─────────────────────────────────

    @Test
    fun `before first climb returns next climb 1 of 3`() {
        val ctx = ClimbContextProvider.computeContext(climbs, progressM = 1000.0)
        assertNull(ctx.activeClimb)
        assertNull(ctx.lastCompletedClimb)
        assertNotNull(ctx.nextClimb)
        assertFalse(ctx.isOnClimb)
        assertTrue(ctx.hasClimbAhead)

        val n = ctx.nextClimb!!
        assertEquals(0, n.index)
        assertEquals("1/3", n.label)
        assertEquals(4000.0, n.distanceToStartM, 0.001)   // 5000 - 1000
        assertEquals(4800.0, n.distanceToEndM, 0.001)      // 5800 - 1000
        assertEquals(0.0, n.progressOnClimbPercent, 0.001)
        assertEquals(60.0, n.remainingElevationM, 0.001)

        assertEquals(4000.0, ctx.distanceToNextClimbStartM!!, 0.001)
        assertNull(ctx.distanceToActiveClimbEndM)
    }

    @Test
    fun `before first climb time to start computed with speed`() {
        val ctx = ClimbContextProvider.computeContext(climbs, progressM = 1000.0, currentSpeedMps = 5.0)
        assertNotNull(ctx.timeToClimbStartSec)
        assertEquals(800.0, ctx.timeToClimbStartSec!!, 0.001)  // 4000m / 5 mps
    }

    @Test
    fun `no time when speed is zero`() {
        val ctx = ClimbContextProvider.computeContext(climbs, progressM = 1000.0, currentSpeedMps = 0.0)
        assertNull(ctx.timeToClimbStartSec)
    }

    // ── at climb start ─────────────────────────────────────

    @Test
    fun `at climb start returns active climb with zero distance to start`() {
        val ctx = ClimbContextProvider.computeContext(climbs, progressM = 5000.0)
        assertTrue(ctx.isOnClimb)
        assertNotNull(ctx.activeClimb)
        // next climb is at 12000m, so not null
        assertNotNull(ctx.nextClimb)
        assertEquals(1, ctx.nextClimb!!.index)
        assertNull(ctx.lastCompletedClimb)

        val a = ctx.activeClimb!!
        assertEquals(0, a.index)
        assertEquals("1/3", a.label)
        assertEquals(0.0, a.distanceToStartM, 0.001)
        assertEquals(800.0, a.distanceToEndM, 0.001)
        assertEquals(0.0, a.progressOnClimbPercent, 0.001)
        assertEquals(60.0, a.remainingElevationM, 0.001)

        assertEquals(800.0, ctx.distanceToActiveClimbEndM!!, 0.001)
        // next climb is at 12000m → 7000m ahead
        assertEquals(7000.0, ctx.distanceToNextClimbStartM!!, 0.001)
    }

    // ── inside climb ───────────────────────────────────────

    @Test
    fun `inside climb returns active climb with remaining distance and elevation`() {
        val ctx = ClimbContextProvider.computeContext(climbs, progressM = 5200.0)
        assertTrue(ctx.isOnClimb)
        assertNotNull(ctx.activeClimb)

        val a = ctx.activeClimb!!
        assertEquals(-200.0, a.distanceToStartM, 0.001)   // 5000 - 5200
        assertEquals(600.0, a.distanceToEndM, 0.001)       // 5800 - 5200
        // 200m into 800m climb = 25%
        assertEquals(25.0, a.progressOnClimbPercent, 0.001)
        // remaining = 60 * (1 - 200/800) = 45
        assertEquals(45.0, a.remainingElevationM, 0.001)
    }

    @Test
    fun `mid climb remaining elevation and progress update with position`() {
        val early = ClimbContextProvider.computeContext(climbs, progressM = 5200.0)
        val mid = ClimbContextProvider.computeContext(climbs, progressM = 5600.0)
        val nearEnd = ClimbContextProvider.computeContext(climbs, progressM = 5750.0)

        assertTrue(early.activeClimb!!.remainingElevationM > mid.activeClimb!!.remainingElevationM)
        assertTrue(mid.activeClimb!!.remainingElevationM > nearEnd.activeClimb!!.remainingElevationM)
        assertTrue(early.activeClimb!!.progressOnClimbPercent < mid.activeClimb!!.progressOnClimbPercent)
        assertTrue(mid.activeClimb!!.progressOnClimbPercent < nearEnd.activeClimb!!.progressOnClimbPercent)
    }

    // ── at climb end ───────────────────────────────────────

    @Test
    fun `at climb end returns completed climb and no longer active`() {
        val ctx = ClimbContextProvider.computeContext(climbs, progressM = 5800.0)
        assertFalse(ctx.isOnClimb)
        assertNull(ctx.activeClimb)
        assertNotNull(ctx.lastCompletedClimb)

        val lc = ctx.lastCompletedClimb!!
        assertEquals(0, lc.index)
        assertEquals("1/3", lc.label)
        assertEquals(0.0, lc.remainingElevationM, 0.001)
        assertEquals(100.0, lc.progressOnClimbPercent, 0.001)
    }

    // ── between climbs ─────────────────────────────────────

    @Test
    fun `between climbs returns last completed and next climb`() {
        val ctx = ClimbContextProvider.computeContext(climbs, progressM = 7000.0)
        assertFalse(ctx.isOnClimb)
        assertNull(ctx.activeClimb)
        assertNotNull(ctx.lastCompletedClimb)
        assertNotNull(ctx.nextClimb)

        assertEquals(0, ctx.lastCompletedClimb!!.index)
        assertEquals(100.0, ctx.lastCompletedClimb!!.progressOnClimbPercent, 0.001)

        assertEquals(1, ctx.nextClimb!!.index)
        assertEquals("2/3", ctx.nextClimb!!.label)
        assertEquals(5000.0, ctx.nextClimb!!.distanceToStartM, 0.001)  // 12000 - 7000
        assertEquals(7000.0, ctx.nextClimb!!.distanceToEndM, 0.001)     // 14000 - 7000
        assertEquals(0.0, ctx.nextClimb!!.progressOnClimbPercent, 0.001)
        assertEquals(100.0, ctx.nextClimb!!.remainingElevationM, 0.001)

        assertEquals(5000.0, ctx.distanceToNextClimbStartM!!, 0.001)
        assertNull(ctx.distanceToActiveClimbEndM)
    }

    // ── inside last climb ──────────────────────────────────

    @Test
    fun `inside last climb shows active 3 slash 3`() {
        val ctx = ClimbContextProvider.computeContext(climbs, progressM = 20100.0)
        assertTrue(ctx.isOnClimb)
        assertNotNull(ctx.activeClimb)
        assertEquals(2, ctx.activeClimb!!.index)
        assertEquals("3/3", ctx.activeClimb!!.label)
    }

    // ── after all climbs ───────────────────────────────────

    @Test
    fun `after all climbs returns last completed and no next`() {
        val ctx = ClimbContextProvider.computeContext(climbs, progressM = 30000.0)
        assertNull(ctx.activeClimb)
        assertNull(ctx.nextClimb)
        assertNotNull(ctx.lastCompletedClimb)
        assertFalse(ctx.isOnClimb)
        assertFalse(ctx.hasClimbAhead)
    }

    // ── average grade ──────────────────────────────────────

    @Test
    fun `average grade calculated correctly`() {
        val ctx = ClimbContextProvider.computeContext(climbs, progressM = 1000.0)
        assertEquals(7.5, ctx.nextClimb!!.averageGradePercent, 0.001)  // 60/800*100
    }

    // ── climb label ────────────────────────────────────────

    @Test
    fun `climb label 3 slash 7`() {
        val sevenClimbs = (1..7).map { RawClimbData(it * 1000.0, 200.0, 20.0) }
        val early = ClimbContextProvider.computeContext(sevenClimbs, progressM = 500.0)
        assertEquals("1/7", early.nextClimb!!.label)
        val onLast = ClimbContextProvider.computeContext(sevenClimbs, progressM = 7100.0)
        assertEquals("7/7", onLast.activeClimb!!.label)
    }

    // ── edge: length zero ──────────────────────────────────

    @Test
    fun `zero length climb does not crash`() {
        val badClimbs = listOf(RawClimbData(1000.0, 0.0, 10.0))
        // start ≤ progress < start+0 is impossible → skip to completed
        val ctx = ClimbContextProvider.computeContext(badClimbs, progressM = 1000.0)
        assertFalse(ctx.isOnClimb)
        assertNull(ctx.activeClimb)
        assertNotNull(ctx.lastCompletedClimb)
        assertEquals(0.0, ctx.lastCompletedClimb!!.remainingElevationM, 0.001)
    }

    // ── edge: elevation zero ───────────────────────────────

    @Test
    fun `zero elevation climb does not crash`() {
        val flatClimbs = listOf(RawClimbData(1000.0, 500.0, 0.0))
        val ctx = ClimbContextProvider.computeContext(flatClimbs, progressM = 1200.0)
        assertTrue(ctx.isOnClimb)
        assertEquals(0.0, ctx.activeClimb!!.averageGradePercent, 0.001)
        assertEquals(0.0, ctx.activeClimb!!.remainingElevationM, 0.001)
    }

    // ── isClimbAhead ───────────────────────────────────────

    @Test
    fun `isClimbAhead respects threshold`() {
        val ctx = ClimbContextProvider.computeContext(climbs, progressM = 1000.0)
        assertTrue(ClimbContextProvider.isClimbAhead(ctx, 5000.0))
        assertFalse(ClimbContextProvider.isClimbAhead(ctx, 3000.0))
    }

    @Test
    fun `isClimbAhead returns true when on first climb with more climbs ahead`() {
        val ctx = ClimbContextProvider.computeContext(climbs, progressM = 5200.0)
        // second climb is at 12000 → 6800m ahead, within 10000 threshold
        assertTrue(ClimbContextProvider.isClimbAhead(ctx, 10000.0))
    }

    @Test
    fun `isClimbAhead returns false when on last climb`() {
        val ctx = ClimbContextProvider.computeContext(climbs, progressM = 20100.0)
        assertFalse(ClimbContextProvider.isClimbAhead(ctx, 10000.0))
    }

    @Test
    fun `isClimbAhead returns false when past all climbs`() {
        val ctx = ClimbContextProvider.computeContext(climbs, progressM = 30000.0)
        assertFalse(ClimbContextProvider.isClimbAhead(ctx, 10000.0))
    }

    // ── difficulty thresholds ──────────────────────────────

    @Test
    fun `difficulty reflects grade`() {
        val flat = ClimbContextProvider.computeContext(
            listOf(RawClimbData(1000.0, 500.0, 5.0)), progressM = 500.0   // 1.0%
        )
        assertEquals(ClimbDifficulty.FLAT, flat.difficultyBucket)

        val rolling = ClimbContextProvider.computeContext(
            listOf(RawClimbData(1000.0, 500.0, 15.0)), progressM = 500.0  // 3.0%
        )
        assertEquals(ClimbDifficulty.ROLLING, rolling.difficultyBucket)

        val moderate = ClimbContextProvider.computeContext(
            listOf(RawClimbData(1000.0, 500.0, 25.0)), progressM = 500.0  // 5.0%
        )
        assertEquals(ClimbDifficulty.MODERATE, moderate.difficultyBucket)

        val steep = ClimbContextProvider.computeContext(
            listOf(RawClimbData(1000.0, 500.0, 45.0)), progressM = 500.0  // 9.0%
        )
        assertEquals(ClimbDifficulty.STEEP, steep.difficultyBucket)

        val verySteep = ClimbContextProvider.computeContext(
            listOf(RawClimbData(1000.0, 500.0, 80.0)), progressM = 500.0  // 16.0%
        )
        assertEquals(ClimbDifficulty.VERY_STEEP, verySteep.difficultyBucket)
    }

    // ── clamped: remaining elevation ────────────────────────

    @Test
    fun `remaining elevation clamped zero on completed climb`() {
        val ctx = ClimbContextProvider.computeContext(climbs, progressM = 5800.0)
        assertEquals(0.0, ctx.lastCompletedClimb!!.remainingElevationM, 0.001)
    }

    @Test
    fun `remaining elevation equals total for next climb`() {
        val ctx = ClimbContextProvider.computeContext(climbs, progressM = 1000.0)
        assertEquals(60.0, ctx.nextClimb!!.remainingElevationM, 0.001)
    }

    @Test
    fun `remaining elevation near climb end approaches zero`() {
        val ctx = ClimbContextProvider.computeContext(climbs, progressM = 5799.0)
        // 5799 is inside [5000, 5800), so active
        assertTrue(ctx.isOnClimb)
        val remain = ctx.activeClimb!!.remainingElevationM
        assertTrue(remain > 0.0)
        assertTrue(remain < 1.0)
    }

    // ── clamped: progress percent ──────────────────────────

    @Test
    fun `progress percent clamped zero before climb`() {
        val ctx = ClimbContextProvider.computeContext(climbs, progressM = 1000.0)
        assertEquals(0.0, ctx.nextClimb!!.progressOnClimbPercent, 0.001)
    }

    @Test
    fun `progress percent clamped 100 after climb`() {
        val ctx = ClimbContextProvider.computeContext(climbs, progressM = 5800.0)
        val lc = ctx.lastCompletedClimb!!
        assertEquals(100.0, lc.progressOnClimbPercent, 0.001)
    }

    @Test
    fun `progress percent midpoint`() {
        val ctx = ClimbContextProvider.computeContext(climbs, progressM = 5400.0)
        // 400m into 800m climb = 50%
        assertEquals(50.0, ctx.activeClimb!!.progressOnClimbPercent, 0.001)
    }

    // ── unsorted climbs ────────────────────────────────────

    @Test
    fun `unsorted climbs are handled by sort`() {
        val unsorted = listOf(
            RawClimbData(20000.0, 500.0, 10.0),
            RawClimbData(5000.0, 800.0, 60.0),
            RawClimbData(12000.0, 2000.0, 100.0),
        )
        val ctx = ClimbContextProvider.computeContext(unsorted, progressM = 1000.0)
        assertEquals("1/3", ctx.nextClimb!!.label)
        assertEquals(5000.0, ctx.nextClimb!!.startDistanceM, 0.001)
        assertEquals(3, ctx.climbCount)
    }

    // ── completed climb info ───────────────────────────────

    @Test
    fun `completed climb has correct fields`() {
        val ctx = ClimbContextProvider.computeContext(climbs, progressM = 5800.0)
        val lc = ctx.lastCompletedClimb!!
        assertEquals(0, lc.index)
        assertEquals(5000.0, lc.startDistanceM, 0.001)
        assertEquals(800.0, lc.lengthM, 0.001)
        assertEquals(0.0, lc.remainingElevationM, 0.001)
        assertEquals(100.0, lc.progressOnClimbPercent, 0.001)
        assertTrue(lc.distanceToStartM < 0.0)  // past the start
        assertTrue(lc.distanceToEndM <= 0.0)    // at or past the end
        assertEquals(ClimbDifficulty.STEEP, lc.difficultyBucket) // 7.5%
    }

    // ── context computed properties ────────────────────────

    @Test
    fun `hasClimbAhead false when on last climb`() {
        val ctx = ClimbContextProvider.computeContext(climbs, progressM = 20100.0)
        assertTrue(ctx.isOnClimb)
        assertFalse(ctx.hasClimbAhead)
    }

    @Test
    fun `hasClimbAhead true when on early climb with more ahead`() {
        val ctx = ClimbContextProvider.computeContext(climbs, progressM = 5200.0)
        assertTrue(ctx.isOnClimb)
        assertTrue(ctx.hasClimbAhead)
    }

    @Test
    fun `hasClimbAhead true before next climb`() {
        val ctx = ClimbContextProvider.computeContext(climbs, progressM = 7000.0)
        assertFalse(ctx.isOnClimb)
        assertTrue(ctx.hasClimbAhead)
    }

    @Test
    fun `distanceToNextClimbStartM null when no next`() {
        val ctx = ClimbContextProvider.computeContext(climbs, progressM = 30000.0)
        assertNull(ctx.distanceToNextClimbStartM)
    }

    @Test
    fun `distanceToActiveClimbEndM returns distance to end of active climb`() {
        val ctx = ClimbContextProvider.computeContext(climbs, progressM = 5200.0)
        assertEquals(600.0, ctx.distanceToActiveClimbEndM!!, 0.001)
    }

    @Test
    fun `distanceToActiveClimbEndM null when not on climb`() {
        val ctx = ClimbContextProvider.computeContext(climbs, progressM = 1000.0)
        assertNull(ctx.distanceToActiveClimbEndM)
    }
}
