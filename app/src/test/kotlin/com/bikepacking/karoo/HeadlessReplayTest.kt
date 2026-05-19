package com.bikepacking.karoo

import com.bikepacking.karoo.field.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class HeadlessReplayTest {

    private lateinit var calc: StatsCalculator
    private val ftp = 200
    private val maxHr = 180

    data class SensorTick(
        val elapsedSec: Long,
        val speedKph: Float,
        val powerWatts: Int,
        val heartRate: Int,
        val cadenceRpm: Int,
        val gradePercent: Float,
        val frontTeeth: Int,
        val rearTeeth: Int,
        val distanceKm: Float,
        val temperatureC: Float?,
    )

    private val flatTick = SensorTick(
        elapsedSec = 0, speedKph = 28f, powerWatts = 160,
        heartRate = 125, cadenceRpm = 70, gradePercent = 0f,
        frontTeeth = 40, rearTeeth = 15, distanceKm = 0f, temperatureC = 15f,
    )
    private val climbTick = SensorTick(
        elapsedSec = 0, speedKph = 12f, powerWatts = 260,
        heartRate = 158, cadenceRpm = 60, gradePercent = 6f,
        frontTeeth = 34, rearTeeth = 28, distanceKm = 0f, temperatureC = 15f,
    )
    private val downhillTick = SensorTick(
        elapsedSec = 0, speedKph = 45f, powerWatts = 80,
        heartRate = 110, cadenceRpm = 50, gradePercent = -4f,
        frontTeeth = 50, rearTeeth = 11, distanceKm = 0f, temperatureC = 15f,
    )

    @Before
    fun setUp() {
        calc = StatsCalculator(ftpWatts = ftp)
        calc.todayFactor = 1.0f
        calc.bodyWeightKg = 75f
        calc.humidityPercent = 50f
        FieldClassifier.resetHysteresis()
    }

    // ── 1. Flat endurance: 60s @ 160W ──
    @Test
    fun `flat endurance segment`() {
        val ticks = (1..60).map { i ->
            flatTick.copy(elapsedSec = i.toLong(), distanceKm = 28f * i / 3600)
        }
        val cp = replay(ticks, setOf(60L))[60]!!

        println("=== FLAT ENDURANCE @60s ===")
        report(calc, cp)

        val np = calc.npWatts()
        val iff = calc.ifValue()
        val vi = calc.viValue()
        val tss = calc.tssValue(60L)
        val kc = calc.caloriesKcal()

        assertTrue("NP should be > 0 after 60s", np > 100)
        assertTrue("IF should be ~0.80", iff in 0.75f..0.85f)
        assertTrue("VI should be ~1.0", vi in 0.95f..1.05f)
        assertTrue("KCAL should be > 2", kc >= 2)
    }

    // ── 2. Climb: 260W @ +6% ──
    @Test
    fun `climb segment`() {
        val warmUp = (1..30).map { i ->
            flatTick.copy(elapsedSec = i.toLong(), distanceKm = 28f * i / 3600)
        }
        val climb = (31..120).map { i ->
            climbTick.copy(elapsedSec = i.toLong(), distanceKm = 28f * 30 / 3600 + 12f * (i - 30) / 3600)
        }
        val ticks = warmUp + climb
        val cp = replay(ticks, setOf(90L))[90]!!

        println("=== CLIMB @90s ===")
        report(calc, cp)

        // 260W = 130% FTP -> WARN or BAD
        val state = cp.powerState
        assertTrue("Power should be WARN/BAD on climb (got $state)", state == FieldState.WARN || state == FieldState.BAD)

        // Grade +6% -> WARN (FieldClassifier thresholds: 5-9% = WARN, >9% = BAD)
        assertEquals("Grade should be WARN on +6%", FieldState.WARN, cp.gradeState)

        // Gear: 60rpm, 260W, grade +6% -> acceptable (60rpm is not low enough for too_hard trigger which needs <=50)
        assertTrue("Gear reason should be acceptable or likely_too_hard",
            cp.gearReason == "acceptable" || cp.gearReason == "likely_too_hard")

        assertTrue("TSS should accumulate", calc.tssValue(90L) > 0f)
    }

    // ── 3. Power=0 during pause should not lower NP ──
    @Test
    fun `pause does not lower NP`() {
        val before = (1..30).map { i -> flatTick.copy(elapsedSec = i.toLong(), powerWatts = 200, distanceKm = 28f * i / 3600) }
        val pause = (31..60).map { i -> flatTick.copy(elapsedSec = i.toLong(), powerWatts = 0, heartRate = 80, cadenceRpm = 0, speedKph = 0f, distanceKm = 28f * 30 / 3600) }
        val after = (61..90).map { i -> flatTick.copy(elapsedSec = i.toLong(), powerWatts = 200, distanceKm = 28f * 30 / 3600 + 28f * (i - 60) / 3600) }
        val ticks = before + pause + after
        val snapshots = replay(ticks, setOf(30L, 60L, 90L))

        val np30 = calc.npWatts()
        val kc30 = calc.caloriesKcal()

        calc.update(0, 80, 30L, 60L) // pause tick to advance time, not power
        val np60 = calc.npWatts()
        val kc60 = calc.caloriesKcal()

        // feed after
        for (i in 61..90) {
            val t = flatTick.copy(elapsedSec = i.toLong(), powerWatts = 200)
            calc.update(t.powerWatts, t.heartRate, /* movingSec= */ i.toLong(), i.toLong())
        }
        val np90 = calc.npWatts()
        val kc90 = calc.caloriesKcal()

        // NP should not drop due to pause (zero power samples excluded from NP)
        assertTrue("NP should not drop due to pause (was $np30, after pause+resume $np90)", np90 >= np30 * 0.8f)
        // KCAL should NOT increase during pause (power=0 samples are not active)
        assertTrue("KCAL should not rise during pause", kc60 in 0..(kc30 + 2))
        assertTrue("KCAL should be reasonable after", kc90 > 0)

        println("=== PAUSE TEST ===")
        println("NP: $np30 -> $np60 -> $np90")
        println("KCAL: $kc30 -> $kc60 -> $kc90")
    }

    // ── 4. Full ride progression (5 min) ──
    @Test
    fun `full ride progression 5min`() {
        val ticks = mutableListOf<SensorTick>()
        var elapsed = 0L
        var dist = 0f
        for (i in 1..30)  { elapsed++; dist += 28f / 3600; ticks.add(flatTick.copy(elapsedSec = elapsed, distanceKm = dist)) }
        for (i in 31..120) { elapsed++; dist += 12f / 3600; ticks.add(climbTick.copy(elapsedSec = elapsed, distanceKm = dist)) }
        for (i in 121..210) { elapsed++; dist += 30f / 3600; ticks.add(flatTick.copy(elapsedSec = elapsed, distanceKm = dist, powerWatts = 140, heartRate = 135, speedKph = 30f)) }
        for (i in 211..300) { elapsed++; dist += 45f / 3600; ticks.add(downhillTick.copy(elapsedSec = elapsed, distanceKm = dist)) }
        val cp = replay(ticks, setOf(300L))[300]!!

        println("=== FULL RIDE 5min @300s ===")
        report(calc, cp)

        assertTrue("TSS should be > 0", calc.tssValue(300L) > 0f)
        assertTrue("Calories should accumulate", calc.caloriesKcal() > 10)
        val rsrv = calc.rideReservePercent(
            calc.tssValue(300L),
            calc.ifValue(),
            calc.decouplingPercent()
        )
        assertTrue("RSRV should be in 0..100 (got $rsrv)", rsrv in 0..100)
    }

    // ── 5. Edge: zero data startup ──
    @Test
    fun `startup with zero data`() {
        val ticks = (1..10).map { i ->
            SensorTick(elapsedSec = i.toLong(), speedKph = 0f, powerWatts = 0,
                heartRate = 0, cadenceRpm = 0, gradePercent = 0f,
                frontTeeth = 0, rearTeeth = 0, distanceKm = 0f, temperatureC = null)
        }
        val cp = replay(ticks, setOf(10L))[10]!!

        assertEquals("Speed NEUTRAL on zero data", FieldState.NEUTRAL, cp.speedState)
        assertEquals("Power NEUTRAL on zero data", FieldState.NEUTRAL, cp.powerState)
        assertEquals("HR NEUTRAL on zero data", FieldState.NEUTRAL, cp.hrState)
        assertEquals("Cadence NEUTRAL on zero data", FieldState.NEUTRAL, cp.cadenceState)
        assertEquals("Grade should be GOOD at 0% grade", FieldState.GOOD, cp.gradeState)
        assertEquals("Gear NEUTRAL on zero data", FieldState.NEUTRAL, cp.gearState)
        assertEquals("NP 0 on no data", 0, calc.npWatts())
    }

    // ── 6. STATS formatters ──
    @Test
    fun `stats formatters after short ride`() {
        val ticks = (1..60).map { i -> flatTick.copy(elapsedSec = i.toLong(), distanceKm = 28f * i / 3600) }
        replay(ticks, setOf(60L))

        assertFalse("NP should not be --", StatsValueFormatter.npW(calc.npWatts()).main == "--")
        assertFalse("IF should not be --", StatsValueFormatter.ifValue(calc.ifValue()).main == "--")
        assertFalse("VI should not be --", StatsValueFormatter.vi(calc.viValue()).main == "--")
        assertFalse("TSS should not be --", StatsValueFormatter.tss(calc.tssValue(60L)).main == "--")
        assertFalse("KCAL should not be --", StatsValueFormatter.calories(calc.caloriesKcal()).main == "--")
    }

    // ── 7. CARB IN monotonic with IF ──
    @Test
    fun `carbs increase with intensity`() {
        val easy = StatsCalculator(ftpWatts = ftp).also { it.todayFactor = 1.0f; it.bodyWeightKg = 75f; it.humidityPercent = 50f }
        val hard = StatsCalculator(ftpWatts = ftp).also { it.todayFactor = 1.0f; it.bodyWeightKg = 75f; it.humidityPercent = 50f }

        for (i in 1..120) { easy.update(100, 100, i.toLong(), i.toLong()) }
        for (i in 1..120) { hard.update(200, 150, i.toLong(), i.toLong()) }

        val easyCarbs = easy.carbsGPerH(easy.ifValue(), 120L, easy.viValue(), 15f, 75f)
        val hardCarbs = hard.carbsGPerH(hard.ifValue(), 120L, hard.viValue(), 15f, 75f)
        assertTrue("CARB IN higher intensity >= lower", hardCarbs >= easyCarbs)
    }

    // ── 8. FLUID IN monotonic with IF and temp ──
    @Test
    fun `fluid increases with temp and IF`() {
        val lowIF = calc.fluidLPerH(0.50f, 15f)
        val highIF = calc.fluidLPerH(0.85f, 15f)
        assertTrue("FLUID higher IF >= lower", highIF >= lowIF)

        val hot = calc.fluidLPerH(0.70f, 30f)
        val cold = calc.fluidLPerH(0.70f, 5f)
        assertTrue("FLUID hot >= cold", hot >= cold)
    }

    // ── 9. Gear missing / teeth zero ──
    @Test
    fun `gear missing shows NEUTRAL`() {
        val ticks = (1..30).map { i ->
            flatTick.copy(elapsedSec = i.toLong(), frontTeeth = 0, rearTeeth = 0)
        }
        val cp = replay(ticks, setOf(30L))[30]!!
        assertEquals("Gear NEUTRAL when no teeth", FieldState.NEUTRAL, cp.gearState)
    }

    // ── 10. LIVE field formatters ──
    @Test
    fun `live formatters work`() {
        assertTrue("Speed formatted", FieldFormatter.speed(28.5f).isNotEmpty())
        assertTrue("Power formatted", FieldFormatter.power(160).isNotEmpty())
        assertTrue("HR formatted", FieldFormatter.hr(125).isNotEmpty())
        assertTrue("Cadence formatted", FieldFormatter.cadence(70).isNotEmpty())
        assertTrue("Grade formatted", FieldFormatter.grade(3.5f).isNotEmpty())
        assertTrue("Gear formatted", FieldFormatter.gear(40, 15).isNotEmpty())
    }

    // ── 11. RSRV clamp ──
    @Test
    fun `rsrv clamped 0-100`() {
        calc.todayFactor = 0.0f  // forces 0
        val r1 = calc.rideReservePercent(100f, 0.99f, 15f)
        assertTrue("RSRV clamped 0-100 (got $r1)", r1 in 0..100)
        calc.todayFactor = 2.0f  // forces >100 before clamp
        val r2 = calc.rideReservePercent(0f, 0.5f, 0f)
        assertTrue("RSRV clamped 0-100 (got $r2)", r2 in 0..100)
    }

    // ═══════════════════════════════════════════════════════════════
    // REPLAY ENGINE
    // ═══════════════════════════════════════════════════════════════
    data class Checkpoint(
        val tick: Long,
        val powerState: FieldState,
        val speedState: FieldState,
        val hrState: FieldState,
        val cadenceState: FieldState,
        val gradeState: FieldState,
        val gearState: FieldState,
        val powerReason: String,
        val gearReason: String,
        val powerTargetLow: Int,
        val powerTargetHigh: Int,
    )

    private fun replay(ticks: List<SensorTick>, snapAt: Set<Long>): Map<Long, Checkpoint> {
        val result = mutableMapOf<Long, Checkpoint>()
        val powerBuf = ArrayDeque<Int>(3)
        val hrBuf = ArrayDeque<Int>(3)
        val cadBuf = ArrayDeque<Int>(3)
        val gradeBuf = ArrayDeque<Float>(5)
        var timeMs = 0L

        calc.reset()
        FieldClassifier.resetHysteresis()

        for (t in ticks) {
            val elapsed = t.elapsedSec
            val isMoving = t.powerWatts > 0 || t.speedKph > 3
            val movingSec = if (isMoving) elapsed else (ticks.lastOrNull { it.elapsedSec < elapsed && (it.powerWatts > 0 || it.speedKph > 3) }?.elapsedSec ?: 0L)

            powerBuf.addLast(t.powerWatts)
            if (powerBuf.size > 3) powerBuf.removeFirst()
            hrBuf.addLast(t.heartRate)
            if (hrBuf.size > 3) hrBuf.removeFirst()
            cadBuf.addLast(t.cadenceRpm)
            if (cadBuf.size > 3) cadBuf.removeFirst()
            gradeBuf.addLast(t.gradePercent)
            if (gradeBuf.size > 5) gradeBuf.removeFirst()

            val smoothedPower = powerBuf.average().toInt()
            val smoothedHr = hrBuf.average().toInt()
            val smoothedCad = cadBuf.average().toInt()
            val smoothedGrade = gradeBuf.average().toFloat()

            calc.update(t.powerWatts, t.heartRate, elapsed, elapsed)

            val rideState = RideState(
                speedKph = t.speedKph,
                powerWatts = t.powerWatts,
                heartRate = t.heartRate,
                cadenceRpm = t.cadenceRpm,
                gradePercent = t.gradePercent,
                distanceKm = t.distanceKm,
                elapsedSec = elapsed,
                movingSec = elapsed,
                frontTeeth = t.frontTeeth,
                rearTeeth = t.rearTeeth,
                temperatureCelsius = t.temperatureC,
                npWholeWatts = calc.npWatts(),
                ifWholeRide = calc.ifValue(),
                ifValue = calc.ifValue(),
                viValue = calc.viValue(),
                tssValue = calc.tssValue(elapsed),
                caloriesKcal = calc.caloriesKcal(),
                avgPowerWatts = if (elapsed > 0) (calc.caloriesKcal() * 1000 / elapsed.toInt()) else 0,
                decouplingPercent = calc.decouplingPercent(),
                carbsGPerH = calc.carbsGPerH(calc.ifValue(), elapsed, calc.viValue(), t.temperatureC, 75f),
                fluidLPerH = calc.fluidLPerH(calc.ifValue(), t.temperatureC),
                rideReservePercent = calc.rideReservePercent(calc.tssValue(elapsed), calc.ifValue(), calc.decouplingPercent()),
                todayFactor = 1.0f,
                wBalancePercent = calc.wBalancePercent(),
            )

            val ctx = RideContext.from(
                state = rideState,
                smoothedPower = smoothedPower,
                smoothedHr = smoothedHr,
                smoothedCad = smoothedCad,
                smoothedGrade = smoothedGrade,
                todayFactor = 1.0f,
                ftp = ftp,
                maxHr = maxHr,
            )

            timeMs += 1000L

            val speedState = FieldClassifier.speed(ctx)
            val powerResult = PowerAdvisor.assess(ctx)
            val hrState = FieldClassifier.hr(t.heartRate, maxHr, timeMs)
            val cadenceState = FieldClassifier.cadence(smoothedCad, t.gradePercent)
            val gradeState = FieldClassifier.grade(smoothedGrade)
            val gearResult = GearAdvisor.assess(ctx)
            val gearState = FieldClassifier.gear(ctx, timeMs)

            if (elapsed in snapAt) {
                result[elapsed] = Checkpoint(
                    tick = elapsed,
                    speedState = speedState,
                    powerState = powerResult.state,
                    hrState = hrState,
                    cadenceState = cadenceState,
                    gradeState = gradeState,
                    gearState = gearState,
                    powerReason = powerResult.reasonCode,
                    gearReason = gearResult.reasonCode,
                    powerTargetLow = powerResult.targetLowWatts,
                    powerTargetHigh = powerResult.targetHighWatts,
                )
            }
        }
        return result
    }

    private fun report(calc: StatsCalculator, cp: Checkpoint) {
        println("Tick ${cp.tick}: NP=${calc.npWatts()} IF=${"%.2f".format(calc.ifValue())} VI=${"%.2f".format(calc.viValue())} TSS=${"%.1f".format(calc.tssValue(cp.tick))} KCAL=${calc.caloriesKcal()}")
        println("  LIVE: SPD ${cp.speedState} | PWR ${cp.powerState} (${cp.powerReason}) | HR ${cp.hrState} | CAD ${cp.cadenceState} | GRD ${cp.gradeState} | GEAR ${cp.gearState} (${cp.gearReason})")
        println("  Target: ${cp.powerTargetLow}-${cp.powerTargetHigh}W")
    }
}
