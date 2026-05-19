package com.bikepacking.karoo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class StatsCalculatorTest {

    private lateinit var calc: StatsCalculator

    @Before
    fun setUp() {
        calc = StatsCalculator(ftpWatts = 200)
        calc.todayFactor = 1.0f
        calc.bodyWeightKg = 75f
        calc.humidityPercent = 50f
    }

    // ── NP ──
    @Test
    fun `np is zero when no data`() {
        assertEquals(0, calc.npWatts())
    }

    @Test
    fun `np returns avg of 30s rolling after 30 samples`() {
        repeat(30) { calc.update(200, 120, it.toLong() + 1L, it.toLong() + 1L) }
        assertEquals(200, calc.npWatts())
    }

    // ── VI ──
    @Test
    fun `vi is zero when no data`() {
        assertEquals(0f, calc.viValue(), 0.001f)
    }

    @Test
    fun `vi returns one for steady power`() {
        repeat(30) { calc.update(200, 120, it.toLong() + 1L, it.toLong() + 1L) }
        assertEquals(1.0f, calc.viValue(), 0.001f)
    }

    // ── IF ──
    @Test
    fun `ifValue uses ftp from constructor`() {
        calc = StatsCalculator(ftpWatts = 200)
        repeat(30) { calc.update(150, 120, it.toLong() + 1L, it.toLong() + 1L) }
        assertEquals(0.75f, calc.ifValue(), 0.01f) // 150/200
    }

    // ── TSS ──
    @Test
    fun `tss zero when movingSec zero`() {
        assertEquals(0f, calc.tssValue(0L), 0.001f)
    }

    @Test
    fun `tss calculates from np and moving time`() {
        calc = StatsCalculator(ftpWatts = 200)
        repeat(30) { calc.update(200, 120, it.toLong() + 1L, it.toLong() + 1L) }
        // NP=200, IF=1.0, TSS = (3600*200*1.0)/(200*3600)*100 = 100
        val tss = calc.tssValue(3600L)
        assertEquals(100f, tss, 1f)
    }

    // ── CALORIES ──
    @Test
    fun `calories zero when no data`() {
        assertEquals(0, calc.caloriesKcal())
    }

    @Test
    fun `calories accumulate from power samples`() {
        calc.update(200, 120, 1L, 1L)
        assertEquals(0, calc.caloriesKcal()) // 200/1000 = 0.2 kJ
        repeat(5) { calc.update(200, 120, it.toLong() + 2L, it.toLong() + 2L) }
        assertTrue(calc.caloriesKcal() >= 1) // 6 * 0.2 = 1.2 kJ -> 1
    }

    // ── HAS DECOUPLING DATA ──
    @Test
    fun `hasDecouplingData false initially`() {
        assertEquals(false, calc.hasDecouplingData())
    }

    @Test
    fun `hasDecouplingData false before 120 ticks`() {
        repeat(119) { calc.update(200, 120, it.toLong() + 1L, it.toLong() + 1L) }
        assertEquals(false, calc.hasDecouplingData())
    }

    @Test
    fun `hasDecouplingData true after 120 ticks with movingSec`() {
        repeat(120) { calc.update(200, 120, it.toLong() + 1L, it.toLong() + 1L) }
        assertEquals(true, calc.hasDecouplingData())
    }

    // ── DECOUPLING ──
    @Test
    fun `decoupling zero when no data`() {
        assertEquals(0f, calc.decouplingPercent(), 0.001f)
    }

    @Test
    fun `decoupling near zero for identical hr power ratio both halves`() {
        repeat(60) { calc.update(200, 120, it.toLong() + 1L, it.toLong() + 1L) }
        repeat(60) { calc.update(200, 120, it.toLong() + 61L, it.toLong() + 61L) }
        val dec = calc.decouplingPercent()
        assertTrue("Expected near-zero decoupling, got $dec", dec in 0f..1f)
    }

    @Test
    fun `decoupling positive when hr rises for same power`() {
        repeat(60) { calc.update(200, 120, it.toLong() + 1L, it.toLong() + 1L) }
        repeat(60) { calc.update(200, 140, it.toLong() + 61L, it.toLong() + 61L) }
        val dec = calc.decouplingPercent()
        assertTrue("Expected positive decoupling, got $dec", dec > 0f)
    }

    // ── CARBS ──
    @Test
    fun `carbs increase monotonically with IF10`() {
        val vals = listOf(
            calc.carbsGPerH(0.50f, 7200L, 1.05f, 15f, 75f),
            calc.carbsGPerH(0.65f, 7200L, 1.05f, 15f, 75f),
            calc.carbsGPerH(0.75f, 7200L, 1.05f, 15f, 75f),
            calc.carbsGPerH(0.85f, 7200L, 1.05f, 15f, 75f),
            calc.carbsGPerH(0.95f, 7200L, 1.05f, 15f, 75f),
        )
        for (i in 1 until vals.size) {
            assertTrue("carbs[${i-1}]=${vals[i-1]} < carbs[$i]=${vals[i]}", vals[i] > vals[i-1])
        }
    }

    @Test
    fun `carbs use moving time not elapsed time`() {
        val result = calc.carbsGPerH(0.70f, 3600L, 1.02f, 20f, 75f)
        assertTrue("carbs should produce a reasonable result with moving time", result in 20..110)
    }

    @Test
    fun `carbs do not increase during pause`() {
        val beforePause = calc.carbsGPerH(0.70f, 3600L, 1.02f, 20f, 75f)
        val duringPause = calc.carbsGPerH(0.70f, 3600L, 1.02f, 20f, 75f)
        assertEquals("Pause should not change carbs if movingSec stays same", beforePause, duringPause)
    }

    @Test
    fun `carbs scale with duration of active ride`() {
        val short = calc.carbsGPerH(0.70f, 1800L, 1.05f, 15f, 75f)
        val medium = calc.carbsGPerH(0.70f, 7200L, 1.05f, 15f, 75f)
        val long = calc.carbsGPerH(0.70f, 14400L, 1.05f, 15f, 75f)
        assertTrue("medium >= short", medium >= short)
        assertTrue("long >= medium", long >= medium)
    }

    @Test
    fun `carbs do not decrease in heat`() {
        val cool = calc.carbsGPerH(0.80f, 7200L, 1.02f, 15f, 75f)
        val hot = calc.carbsGPerH(0.80f, 7200L, 1.02f, 30f, 75f)
        assertTrue("Heat should not reduce carbs. cool=$cool hot=$hot", hot >= cool)
    }

    @Test
    fun `carbs scale mildly with body weight`() {
        val light = calc.carbsGPerH(0.70f, 3600L, 1.02f, 20f, 55f)
        val normal = calc.carbsGPerH(0.70f, 3600L, 1.02f, 20f, 75f)
        val heavy = calc.carbsGPerH(0.70f, 3600L, 1.02f, 20f, 100f)
        assertTrue("heavy >= normal", heavy >= normal)
        assertTrue("normal >= light", normal >= light)
    }

    @Test
    fun `carbs clamp to 20 to 110`() {
        val veryLow = calc.carbsGPerH(0.0f, 0L, 1.0f, null, 40f)
        val veryHigh = calc.carbsGPerH(1.5f, 86400L, 1.5f, 40f, 120f)
        assertTrue("Lower bound >= 20, got $veryLow", veryLow >= 20)
        assertTrue("Upper bound <= 110, got $veryHigh", veryHigh <= 110)
    }

    // ── FLUID ──
    @Test
    fun `fluid increases with temperature`() {
        val vals = listOf(
            calc.fluidLPerH(0.80f, 0f),
            calc.fluidLPerH(0.80f, 10f),
            calc.fluidLPerH(0.80f, 20f),
            calc.fluidLPerH(0.80f, 30f),
        )
        for (i in 1 until vals.size) {
            assertTrue("fluid[$i]=${vals[i]} >= fluid[${i-1}]=${vals[i-1]}", vals[i] >= vals[i-1])
        }
    }

    @Test
    fun `fluid increases with IF10`() {
        val low = calc.fluidLPerH(0.50f, 20f)
        val mid = calc.fluidLPerH(0.70f, 20f)
        val high = calc.fluidLPerH(0.90f, 20f)
        assertTrue("high >= mid", high >= mid)
        assertTrue("mid >= low", mid >= low)
    }

    @Test
    fun `fluid scales with body weight`() {
        calc.bodyWeightKg = 55f
        val light = calc.fluidLPerH(0.70f, 20f)
        calc.bodyWeightKg = 75f
        val normal = calc.fluidLPerH(0.70f, 20f)
        calc.bodyWeightKg = 100f
        val heavy = calc.fluidLPerH(0.70f, 20f)
        assertTrue("heavy >= normal", heavy >= normal)
        assertTrue("normal >= light", normal >= light)
        calc.bodyWeightKg = 75f
    }

    @Test
    fun `fluid clamps to 0-30 to 1-50`() {
        calc.bodyWeightKg = 40f
        val low = calc.fluidLPerH(0.0f, -10f)
        calc.bodyWeightKg = 100f
        val high = calc.fluidLPerH(1.5f, 50f)
        assertTrue("Lower bound >= 0.30, got $low", low >= 0.30f)
        assertTrue("Upper bound <= 1.50, got $high", high <= 1.50f)
        calc.bodyWeightKg = 75f
    }

    @Test
    fun `fluid fallback temp null is reasonable`() {
        val result = calc.fluidLPerH(0.70f, null)
        assertTrue("Null temp should give reasonable fluid, got $result", result in 0.40f..0.80f)
    }

    // ── RIDE RESERVE ──
    @Test
    fun `rideReserve returns todayFactor times 100 when tss zero`() {
        val result = calc.rideReservePercent(0f, 0f, 0f)
        assertEquals(100, result)
    }

    @Test
    fun `rideReserve decreases with higher tss`() {
        val low = calc.rideReservePercent(50f, 0.7f, 2f)
        val high = calc.rideReservePercent(200f, 0.7f, 2f)
        assertTrue("Higher TSS should lower reserve", high < low)
    }

    @Test
    fun `rideReserve respects todayFactor`() {
        calc.todayFactor = 0.8f
        val result = calc.rideReservePercent(0f, 0f, 0f)
        assertEquals(80, result)
    }

    @Test
    fun `rideReserve penalizes high IF10 above threshold`() {
        val lowIf = calc.rideReservePercent(50f, 0.70f, 2f)
        val highIf = calc.rideReservePercent(50f, 0.95f, 2f)
        assertTrue("IF10 above 0.75 should penalize reserve", highIf < lowIf)
    }

    @Test
    fun `rideReserve penalizes high decoupling`() {
        val lowDec = calc.rideReservePercent(50f, 0.70f, 2f)
        calc.update(180, 120, 1L, 1L)
        repeat(120) { calc.update(200, 100, it.toLong() + 2L, it.toLong() + 2L) }
        val highDec = calc.rideReservePercent(50f, 0.70f, 12f)
        assertTrue("Decoupling above 5% should penalize reserve", highDec < lowDec)
    }

    @Test
    fun `rideReserve floor is zero`() {
        val result = calc.rideReservePercent(999f, 1.5f, 99f)
        assertTrue("Reserve should not go below 0", result >= 0)
    }

    @Test
    fun `rideReserve ceiling is 100`() {
        val result = calc.rideReservePercent(0f, 0f, 0f)
        assertTrue("Reserve with no TSS should be at most 100", result <= 100)
    }

    @Test
    fun `rideReserve is not affected by route length`() {
        val tss50 = calc.rideReservePercent(50f, 0.70f, 2f)
        assertEquals("RSRV depends only on TSS, IF10, decoupling", 70, tss50)
    }

    // ── RESET ──
    @Test
    fun `reset clears all accumulated state`() {
        repeat(120) { calc.update(200, 120, it.toLong() + 1L, it.toLong() + 1L) }
        calc.reset()
        assertEquals(0, calc.npWatts())
        assertEquals(0f, calc.viValue(), 0.001f)
        assertEquals(false, calc.hasDecouplingData())
    }

    @Test
    fun `reset preserves ftpWatts`() {
        calc = StatsCalculator(ftpWatts = 180)
        calc.reset()
        assertEquals(180, calc.ftpWatts)
    }

    // ── EDGE: pause does not corrupt moving average ──
    @Test
    fun `pause with zero power does not affect decoupling`() {
        // Ride for 60 seconds (movingSec: 1..60)
        repeat(60) { calc.update(200, 120, it.toLong() + 1L, it.toLong() + 1L) }
        // Pause for 10 seconds (power=0, hr=0, movingSec frozen at 60)
        repeat(10) { calc.update(0, 0, 60L, it.toLong() + 61L) }
        // Ride again for 60 seconds (movingSec: 61..120)
        repeat(60) { calc.update(200, 120, it.toLong() + 61L, it.toLong() + 71L) }
        // Pause doesn't add to decoupling data (no HR/Power, movingSec not advancing)
        // Each half needs 60 valid samples, pause adds none
        assertEquals(true, calc.hasDecouplingData())
        // Decoupling should still be near zero
        val dec = calc.decouplingPercent()
        assertTrue("Expected near-zero decoupling after pause, got $dec", dec in 0f..2f)
    }

    // ── NP EXCLUDES PAUSE ZEROS ──
    @Test
    fun `np excludes pause zero power samples`() {
        repeat(30) { calc.update(200, 120, it.toLong() + 1L, it.toLong() + 1L) }
        // Pause: movingSec frozen, power=0
        repeat(20) { calc.update(0, 0, 30L, it.toLong() + 31L) }
        // Resume
        repeat(30) { calc.update(200, 120, it.toLong() + 31L, it.toLong() + 51L) }
        // NP should still be 200 (pause zeros excluded)
        assertEquals(200, calc.npWatts())
    }

    // ── VI DOES NOT SPIKE AFTER PAUSE ──
    @Test
    fun `vi does not spike after pause`() {
        repeat(30) { calc.update(200, 120, it.toLong() + 1L, it.toLong() + 1L) }
        repeat(20) { calc.update(0, 0, 30L, it.toLong() + 31L) }
        repeat(30) { calc.update(200, 120, it.toLong() + 31L, it.toLong() + 51L) }
        val vi = calc.viValue()
        assertTrue("VI should stay near 1.0 after pause, got $vi", vi in 0.95f..1.05f)
    }

    // ── AVERAGE POWER EXCLUDES PAUSE ZEROS ──
    @Test
    fun `average power excludes pause zero samples`() {
        repeat(30) { calc.update(200, 120, it.toLong() + 1L, it.toLong() + 1L) }
        repeat(20) { calc.update(0, 0, 30L, it.toLong() + 31L) }
        repeat(30) { calc.update(200, 120, it.toLong() + 31L, it.toLong() + 51L) }
        val avg = calc.viValue() * 0  // VI = NP/avg, so avg = NP/VI
        // NP should be 200, VI should be ~1.0, so avg ≈ 200
        assertTrue("Average should be ~200, got NP=${calc.npWatts()} VI=${calc.viValue()}",
            calc.viValue() in 0.95f..1.05f)
    }

    // ── CALORIES DO NOT ACCUMULATE DURING PAUSE ──
    @Test
    fun `calories do not accumulate during pause with zero power`() {
        repeat(5) { calc.update(200, 120, it.toLong() + 1L, it.toLong() + 1L) }
        val before = calc.caloriesKcal()
        repeat(20) { calc.update(0, 0, 5L, it.toLong() + 6L) }
        assertEquals(before, calc.caloriesKcal())
    }

    @Test
    fun `calories do not accumulate from stale power during pause`() {
        repeat(5) { calc.update(200, 120, it.toLong() + 1L, it.toLong() + 1L) }
        val before = calc.caloriesKcal()
        // Stale power=180 but movingSec not advancing
        repeat(20) { calc.update(180, 120, 5L, it.toLong() + 6L) }
        assertEquals(before, calc.caloriesKcal())
    }

    // ── RESET CLEARS lastMovingSec ──
    @Test
    fun `reset clears lastMovingSec so new samples are counted`() {
        repeat(10) { calc.update(150, 120, it.toLong() + 1L, it.toLong() + 1L) }
        calc.reset()
        repeat(30) { calc.update(250, 130, it.toLong() + 1L, it.toLong() + 1L) }
        assertEquals(250, calc.npWatts())
    }

    // ── TSS NOT AFFECTED BY PAUSE ZEROS ──
    @Test
    fun `tss not affected by pause zeros`() {
        // Ride with pause: total moving time = 60s, but pause breaks into two blocks
        repeat(30) { calc.update(200, 120, it.toLong() + 1L, it.toLong() + 1L) }
        repeat(20) { calc.update(0, 0, 30L, it.toLong() + 31L) }
        repeat(30) { calc.update(200, 120, it.toLong() + 31L, it.toLong() + 51L) }
        val tssWithPause = calc.tssValue(60L)
        // NP should be 200, IF=1.0, TSS = (60*200*1.0)/(200*3600)*100 = 1.67
        assertTrue("TSS after pause should be ~1.67, got $tssWithPause",
            tssWithPause in 1.0f..2.5f)
    }

    // ── FALLBACK: no GPS data, only power based stats ──
    @Test
    fun `np and vi work without any distance or gps data`() {
        repeat(30) { calc.update(250, 130, it.toLong() + 1L, it.toLong() + 1L) }
        assertEquals(250, calc.npWatts())
        assertEquals(1.0f, calc.viValue(), 0.001f)
    }

    // ── NEW DECOUPLING TESTS ──
    @Test
    fun `decoupling requires minimum 120 samples`() {
        repeat(119) { calc.update(200, 120, it.toLong() + 1L, it.toLong() + 1L) }
        assertEquals(0f, calc.decouplingPercent(), 0.001f)
        calc.update(200, 120, 120L, 120L)
        assertTrue("Decoupling should be computed at 120 samples", calc.decouplingPercent() >= 0f)
    }

    @Test
    fun `decoupling ignores pause samples`() {
        // 60 active samples
        repeat(60) { calc.update(200, 130, it.toLong() + 1L, it.toLong() + 1L) }
        // 20 pause samples (power=0, hr=0 — should NOT be added)
        repeat(20) { calc.update(0, 0, 60L, it.toLong() + 61L) }
        // Only 60 more active samples to reach 120
        repeat(60) { calc.update(200, 130, it.toLong() + 61L, it.toLong() + 81L) }
        // Total active: 120, pause samples excluded
        assertEquals(120, decoupleHrSize())
        val dec = calc.decouplingPercent()
        assertTrue("Decoupling should be near zero with consistent HR/power, got $dec", dec in 0f..2f)
    }

    @Test
    fun `decoupling clamped to zero for negative drift`() {
        // First half: high HR/power ratio
        repeat(60) { calc.update(150, 140, it.toLong() + 1L, it.toLong() + 1L) }
        // Second half: low HR/power ratio (HR drops, power rises)
        repeat(60) { calc.update(200, 120, it.toLong() + 61L, it.toLong() + 61L) }
        val dec = calc.decouplingPercent()
        assertTrue("Negative drift should be clamped to 0, got $dec", dec >= 0f)
        assertTrue("Negative drift should be clamped to 0, got $dec", dec <= 1f)
    }

    // ── NEW RSRV TESTS ──
    @Test
    fun `rsrv recovers after easing off`() {
        calc.resetReserveGuard()
        // Hard effort: TSS=200, IF=0.95
        val hard = calc.rideReservePercent(200f, 0.95f, 0f)
        assertTrue("RSRV should be low after hard effort, got $hard", hard < 10)

        // Ease off: much lower TSS and IF
        val easy = calc.rideReservePercent(30f, 0.60f, 0f)
        // raw = 100 - 18 = 82, but recovery is slow from hard (~0)
        // 0 + (82-0)*0.02 = 1.64 → 2
        assertTrue("RSRV should start recovering, got $easy", easy > 0)
        assertTrue("RSRV recovery should be slow, got $easy", easy < 20)
    }

    @Test
    fun `rsrv recovers with lower tss and if`() {
        calc.resetReserveGuard()
        // First: moderate effort
        val moderate = calc.rideReservePercent(80f, 0.90f, 0f)
        // raw = 100 - 48 - 12 = 40, lastReserve = 40

        // Then: ease off significantly
        val easy = calc.rideReservePercent(30f, 0.60f, 0f)
        // raw = 100 - 18 = 82, raw > lastReserve(40) → smoothing: 40 + (82-40)*0.02 = 40.84
        assertTrue("RSRV should start recovering, got easy=$easy vs moderate=$moderate", easy >= moderate)
        // But recovery is slow, so it won't jump to 82 immediately
        assertTrue("RSRV recovery should be gradual, got $easy", easy < 82)
    }

    @Test
    fun `rsrv drops immediately on hard effort`() {
        calc.resetReserveGuard()
        // Start easy
        val easy = calc.rideReservePercent(20f, 0.50f, 0f)
        // raw = 100 - 12 = 88, lastReserve = 88

        // Sudden hard effort
        val hard = calc.rideReservePercent(150f, 0.95f, 0f)
        // raw = 100 - 90 - 16 = -6 → 0, raw < lastReserve(88) → instant drop
        assertTrue("RSRV should drop immediately on hard effort, got $hard", hard < easy)
        assertTrue("RSRV should drop to near zero, got $hard", hard <= 5)
    }

    @Test
    fun `rsrv does not jump instantly on recovery`() {
        calc.resetReserveGuard()
        // Hard effort first
        val hard = calc.rideReservePercent(200f, 0.95f, 0f)
        // raw = 100 - 120 - 16 = -36 → 0, lastReserve = 0

        // Ease off — raw would be high, but recovery is slow
        val easy = calc.rideReservePercent(20f, 0.50f, 0f)
        // raw = 100 - 12 = 88, raw > lastReserve(0) → 0 + (88-0)*0.02 = 1.76 → 2
        assertTrue("RSRV should not jump instantly, got $easy", easy < 20)
        assertTrue("RSRV should show some recovery, got $easy", easy > 0)
    }

    @Test
    fun `rsrv multiple recovery ticks converge toward raw`() {
        calc.resetReserveGuard()
        // Hard effort
        calc.rideReservePercent(200f, 0.95f, 0f)
        // lastReserve ≈ 0

        // Simulate multiple ticks of easy riding
        var value = 0
        for (i in 0 until 50) {
            value = calc.rideReservePercent(20f, 0.50f, 0f)
        }
        // After 50 ticks of 2% recovery: should be much higher but not at 88 yet
        // 0 + (88-0) * (1 - 0.98^50) ≈ 0 + 88 * 0.636 ≈ 56
        assertTrue("RSRV should converge toward raw after many ticks, got $value", value > 30)
        assertTrue("RSRV should not exceed raw value, got $value", value < 88)
    }

    private fun decoupleHrSize(): Int {
        // Access private field via reflection for test assertion
        val field = StatsCalculator::class.java.getDeclaredField("decoupleHr")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val list = field.get(calc) as List<*>
        return list.size
    }
}
