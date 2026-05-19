package com.bikepacking.karoo.message

import com.bikepacking.karoo.field.ClimbInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClimbPowerTargetCalculatorTest {

    private val defaultFtp = 275

    private fun shortSteepClimb(
        index: Int = 0,
        total: Int = 3,
        startDistanceM: Double = 5000.0,
    ): ClimbInfo = ClimbInfo(
        index = index,
        total = total,
        startDistanceM = startDistanceM,
        lengthM = 1000.0,
        totalElevationM = 80.0,
        distanceToStartM = 0.0,
        distanceToEndM = 1000.0,
        remainingElevationM = 60.0,
        progressOnClimbPercent = 25.0,
    )

    private fun longClimb(
        index: Int = 0,
        total: Int = 3,
        startDistanceM: Double = 5000.0,
    ): ClimbInfo = ClimbInfo(
        index = index,
        total = total,
        startDistanceM = startDistanceM,
        lengthM = 6000.0,
        totalElevationM = 300.0,
        distanceToStartM = 0.0,
        distanceToEndM = 6000.0,
        remainingElevationM = 200.0,
        progressOnClimbPercent = 33.0,
    )

    private fun moderateClimb(
        index: Int = 0,
        total: Int = 3,
        startDistanceM: Double = 5000.0,
    ): ClimbInfo = ClimbInfo(
        index = index,
        total = total,
        startDistanceM = startDistanceM,
        lengthM = 3000.0,
        totalElevationM = 150.0,
        distanceToStartM = 0.0,
        distanceToEndM = 3000.0,
        remainingElevationM = 100.0,
        progressOnClimbPercent = 33.0,
    )

    private fun gentleClimb(
        index: Int = 0,
        total: Int = 3,
        startDistanceM: Double = 5000.0,
    ): ClimbInfo = ClimbInfo(
        index = index,
        total = total,
        startDistanceM = startDistanceM,
        lengthM = 2000.0,
        totalElevationM = 50.0,
        distanceToStartM = 0.0,
        distanceToEndM = 2000.0,
        remainingElevationM = 25.0,
        progressOnClimbPercent = 50.0,
    )

    // ── null / edge cases ───────────────────────────────────

    @Test
    fun `null FTP returns null`() {
        val result = ClimbPowerTargetCalculator.calculate(
            activeClimb = shortSteepClimb(),
            ftp = 0,
            todayFactor = 1.0f,
        )
        assertNull(result)
    }

    @Test
    fun `null activeClimb returns null`() {
        val result = ClimbPowerTargetCalculator.calculate(
            activeClimb = null,
            ftp = defaultFtp,
            todayFactor = 1.0f,
        )
        assertNull(result)
    }

    @Test
    fun `negative FTP returns null`() {
        val result = ClimbPowerTargetCalculator.calculate(
            activeClimb = shortSteepClimb(),
            ftp = -1,
            todayFactor = 1.0f,
        )
        assertNull(result)
    }

    @Test
    fun `null remaining route data still returns valid target`() {
        val result = ClimbPowerTargetCalculator.calculate(
            activeClimb = moderateClimb(),
            ftp = defaultFtp,
            todayFactor = 1.0f,
            wBalancePercent = null,
            hrDrift = null,
            routeAscentRemainingM = null,
            routeDistanceRemainingKm = null,
        )
        assertNotNull(result)
        assertTrue(result!!.targetMidW > 0)
    }

    // ── climb type ──────────────────────────────────────────

    @Test
    fun `short steep climb allows higher target than long climb`() {
        val shortResult = ClimbPowerTargetCalculator.calculate(
            activeClimb = shortSteepClimb(),
            ftp = defaultFtp,
            todayFactor = 1.0f,
        )
        val longResult = ClimbPowerTargetCalculator.calculate(
            activeClimb = longClimb(),
            ftp = defaultFtp,
            todayFactor = 1.0f,
        )
        assertNotNull(shortResult)
        assertNotNull(longResult)
        assertTrue(
            "short steep mid (${shortResult!!.targetMidW}) should be > long mid (${longResult!!.targetMidW})",
            shortResult.targetMidW > longResult.targetMidW
        )
    }

    @Test
    fun `long climb target is lower than moderate climb target`() {
        val longResult = ClimbPowerTargetCalculator.calculate(
            activeClimb = longClimb(),
            ftp = defaultFtp,
            todayFactor = 1.0f,
        )
        val modResult = ClimbPowerTargetCalculator.calculate(
            activeClimb = moderateClimb(),
            ftp = defaultFtp,
            todayFactor = 1.0f,
        )
        assertNotNull(longResult)
        assertNotNull(modResult)
        assertTrue(
            "long mid (${longResult!!.targetMidW}) should be < moderate mid (${modResult!!.targetMidW})",
            longResult.targetMidW < modResult.targetMidW
        )
    }

    // ── route remaining penalties ───────────────────────────

    @Test
    fun `lots of UP left lowers target`() {
        val baseResult = ClimbPowerTargetCalculator.calculate(
            activeClimb = moderateClimb(),
            ftp = defaultFtp,
            todayFactor = 1.0f,
            routeAscentRemainingM = null,
        )
        val penalizedResult = ClimbPowerTargetCalculator.calculate(
            activeClimb = moderateClimb(),
            ftp = defaultFtp,
            todayFactor = 1.0f,
            routeAscentRemainingM = 2500.0,
        )
        assertNotNull(baseResult)
        assertNotNull(penalizedResult)
        assertTrue(
            "mid with UP left (${penalizedResult!!.targetMidW}) should be < mid without (${baseResult!!.targetMidW})",
            penalizedResult.targetMidW < baseResult.targetMidW
        )
    }

    @Test
    fun `lot of distance left lowers target`() {
        val baseResult = ClimbPowerTargetCalculator.calculate(
            activeClimb = moderateClimb(),
            ftp = defaultFtp,
            todayFactor = 1.0f,
            routeDistanceRemainingKm = 10f,
        )
        val penalizedResult = ClimbPowerTargetCalculator.calculate(
            activeClimb = moderateClimb(),
            ftp = defaultFtp,
            todayFactor = 1.0f,
            routeDistanceRemainingKm = 100f,
        )
        assertNotNull(baseResult)
        assertNotNull(penalizedResult)
        assertTrue(
            "mid with 100km left (${penalizedResult!!.targetMidW}) should be < mid with 10km left (${baseResult!!.targetMidW})",
            penalizedResult.targetMidW < baseResult.targetMidW
        )
    }

    @Test
    fun `ascent penalty scales with magnitude`() {
        val low = ClimbPowerTargetCalculator.calculate(
            activeClimb = moderateClimb(), ftp = defaultFtp, todayFactor = 1.0f,
            routeAscentRemainingM = 600.0,
        )!!
        val medium = ClimbPowerTargetCalculator.calculate(
            activeClimb = moderateClimb(), ftp = defaultFtp, todayFactor = 1.0f,
            routeAscentRemainingM = 1500.0,
        )!!
        val high = ClimbPowerTargetCalculator.calculate(
            activeClimb = moderateClimb(), ftp = defaultFtp, todayFactor = 1.0f,
            routeAscentRemainingM = 3000.0,
        )!!
        assertTrue("low ($low) mid ${low.targetMidW} > medium ($medium) mid ${medium.targetMidW}",
            low.targetMidW >= medium.targetMidW)
        assertTrue("medium ($medium) mid ${medium.targetMidW} > high ($high) mid ${high.targetMidW}",
            medium.targetMidW >= high.targetMidW)
    }

    // ── W'bal penalties ─────────────────────────────────────

    @Test
    fun `low Wbal lowers target`() {
        val baseResult = ClimbPowerTargetCalculator.calculate(
            activeClimb = moderateClimb(),
            ftp = defaultFtp,
            todayFactor = 1.0f,
            wBalancePercent = 60,
        )
        val penalizedResult = ClimbPowerTargetCalculator.calculate(
            activeClimb = moderateClimb(),
            ftp = defaultFtp,
            todayFactor = 1.0f,
            wBalancePercent = 25,
        )
        assertNotNull(baseResult)
        assertNotNull(penalizedResult)
        assertTrue(
            "mid with wBal=25 (${penalizedResult!!.targetMidW}) should be < mid with wBal=60 (${baseResult!!.targetMidW})",
            penalizedResult.targetMidW < baseResult.targetMidW
        )
    }

    @Test
    fun `wBal penalty becomes more aggressive below 15`() {
        val moderatePenalty = ClimbPowerTargetCalculator.calculate(
            activeClimb = moderateClimb(), ftp = defaultFtp, todayFactor = 1.0f,
            wBalancePercent = 25,
        )!!
        val severePenalty = ClimbPowerTargetCalculator.calculate(
            activeClimb = moderateClimb(), ftp = defaultFtp, todayFactor = 1.0f,
            wBalancePercent = 10,
        )!!
        assertTrue(
            "wBal=10 severe ($severePenalty) mid ${severePenalty.targetMidW} < wBal=25 moderate ($moderatePenalty) mid ${moderatePenalty.targetMidW}",
            severePenalty.targetMidW < moderatePenalty.targetMidW
        )
    }

    // ── HR drift penalties ──────────────────────────────────

    @Test
    fun `high HR drift lowers target`() {
        val baseResult = ClimbPowerTargetCalculator.calculate(
            activeClimb = moderateClimb(),
            ftp = defaultFtp,
            todayFactor = 1.0f,
            hrDrift = 2f,
        )
        val penalizedResult = ClimbPowerTargetCalculator.calculate(
            activeClimb = moderateClimb(),
            ftp = defaultFtp,
            todayFactor = 1.0f,
            hrDrift = 7f,
        )
        assertNotNull(baseResult)
        assertNotNull(penalizedResult)
        assertTrue(
            "mid with drift=7f (${penalizedResult!!.targetMidW}) should be < mid with drift=2f (${baseResult!!.targetMidW})",
            penalizedResult.targetMidW < baseResult.targetMidW
        )
    }

    @Test
    fun `HR drift above 10 gives stronger penalty than above 5`() {
        val moderatePenalty = ClimbPowerTargetCalculator.calculate(
            activeClimb = moderateClimb(), ftp = defaultFtp, todayFactor = 1.0f,
            hrDrift = 7f,
        )!!
        val severePenalty = ClimbPowerTargetCalculator.calculate(
            activeClimb = moderateClimb(), ftp = defaultFtp, todayFactor = 1.0f,
            hrDrift = 12f,
        )!!
        assertTrue(
            "drift=12f severe ($severePenalty) mid ${severePenalty.targetMidW} < drift=7f moderate ($moderatePenalty) mid ${moderatePenalty.targetMidW}",
            severePenalty.targetMidW < moderatePenalty.targetMidW
        )
    }

    // ── TodayFactor ─────────────────────────────────────────

    @Test
    fun `TodayFactor affects target`() {
        val freshResult = ClimbPowerTargetCalculator.calculate(
            activeClimb = shortSteepClimb(),
            ftp = 300,
            todayFactor = 1.0f,
        )
        val tiredResult = ClimbPowerTargetCalculator.calculate(
            activeClimb = shortSteepClimb(),
            ftp = 300,
            todayFactor = 0.7f,
        )
        assertNotNull(freshResult)
        assertNotNull(tiredResult)
        assertTrue(
            "todayFactor=1.0 mid (${freshResult!!.targetMidW}) should be < todayFactor=0.7 mid (${tiredResult!!.targetMidW})",
            freshResult.targetMidW > tiredResult.targetMidW
        )
    }

    // ── invariant low < mid < high ──────────────────────────

    @Test
    fun `targetLow is less than targetMid which is less than targetHigh for short climb`() {
        assertLowMidLow(setOf(
            ClimbPowerTargetCalculator.calculate(
                activeClimb = shortSteepClimb(),
                ftp = defaultFtp, todayFactor = 1.0f,
            ),
            ClimbPowerTargetCalculator.calculate(
                activeClimb = shortSteepClimb(),
                ftp = defaultFtp, todayFactor = 0.8f,
            ),
        ))
    }

    @Test
    fun `targetLow is less than targetMid which is less than targetHigh for long climb`() {
        assertLowMidLow(setOf(
            ClimbPowerTargetCalculator.calculate(
                activeClimb = longClimb(),
                ftp = defaultFtp, todayFactor = 1.0f,
            ),
            ClimbPowerTargetCalculator.calculate(
                activeClimb = longClimb(),
                ftp = defaultFtp, todayFactor = 0.8f,
                wBalancePercent = 20,
            ),
        ))
    }

    @Test
    fun `targetLow is less than targetMid which is less than targetHigh for moderate climb with penalties`() {
        assertLowMidLow(setOf(
            ClimbPowerTargetCalculator.calculate(
                activeClimb = moderateClimb(),
                ftp = defaultFtp, todayFactor = 1.0f,
                wBalancePercent = 40, hrDrift = 6f,
                routeAscentRemainingM = 600.0,
            ),
        ))
    }

    private fun assertLowMidLow(results: Collection<ClimbPowerTarget?>) {
        for (r in results) {
            assertNotNull(r)
            val msg = "low=${r!!.targetLowW} mid=${r.targetMidW} high=${r.targetHighW}"
            assertTrue("$msg — low should be < mid", r.targetLowW < r.targetMidW)
            assertTrue("$msg — mid should be < high", r.targetMidW < r.targetHighW)
        }
    }

    // ── clamp / boundary ────────────────────────────────────

    @Test
    fun `target is clamped to endurance minimum`() {
        val result = ClimbPowerTargetCalculator.calculate(
            activeClimb = longClimb(),
            ftp = 200,
            todayFactor = 0.5f,
            wBalancePercent = 5,
            hrDrift = 15f,
            routeAscentRemainingM = 5000.0,
            routeDistanceRemainingKm = 200f,
        )
        assertNotNull(result)
        val adjustedFtp = (200 * 0.5f + 0.5f).toInt().coerceAtLeast(50)
        val enduranceMin = (adjustedFtp * 0.55f + 0.5f).toInt()
        assertTrue(
            "mid ${result!!.targetMidW} should be >= enduranceMin $enduranceMin",
            result.targetMidW >= enduranceMin
        )
        assertTrue(
            "low ${result.targetLowW} should be >= enduranceMin $enduranceMin",
            result.targetLowW >= enduranceMin
        )
    }

    @Test
    fun `target is clamped to tempo maximum`() {
        val result = ClimbPowerTargetCalculator.calculate(
            activeClimb = shortSteepClimb(),
            ftp = 400,
            todayFactor = 1.1f,
        )
        assertNotNull(result)
        val adjustedFtp = (400 * 1.1f + 0.5f).toInt().coerceAtLeast(50)
        val tempoMax = (adjustedFtp * 1.05f + 0.5f).toInt()
        assertTrue(
            "mid ${result!!.targetMidW} should be <= tempoMax $tempoMax",
            result.targetMidW <= tempoMax
        )
        assertTrue(
            "high ${result.targetHighW} should be <= tempoMax $tempoMax",
            result.targetHighW <= tempoMax
        )
    }

    // ── reasonCode ──────────────────────────────────────────

    @Test
    fun `reasonCode contains expected segments`() {
        val result = ClimbPowerTargetCalculator.calculate(
            activeClimb = longClimb(),
            ftp = defaultFtp,
            todayFactor = 1.0f,
            wBalancePercent = 25,
            hrDrift = 7f,
            routeAscentRemainingM = 2500.0,
            routeDistanceRemainingKm = 100f,
        )
        assertNotNull(result)
        val code = result!!.reasonCode
        assertTrue("reasonCode '$code' should contain 'long'", code.contains("long"))
        assertTrue("reasonCode '$code' should contain 'up+2k'", code.contains("up+2k"))
        assertTrue("reasonCode '$code' should contain '80+km'", code.contains("80+km"))
        assertTrue("reasonCode '$code' should contain 'wbal30'", code.contains("wbal30"))
        assertTrue("reasonCode '$code' should contain 'drift5'", code.contains("drift5"))
    }

    @Test
    fun `reasonCode contains only climb type when no penalties apply`() {
        val result = ClimbPowerTargetCalculator.calculate(
            activeClimb = gentleClimb(),
            ftp = defaultFtp,
            todayFactor = 1.0f,
        )
        assertNotNull(result)
        assertEquals("moderate", result!!.reasonCode)
    }
}
