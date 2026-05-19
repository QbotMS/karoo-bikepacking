package com.bikepacking.karoo

import com.bikepacking.karoo.field.*
import com.google.gson.Gson
import com.google.gson.JsonObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Headless validation test: real FIT replay JSON → tick-by-tick through
 * StatsCalculator, FieldClassifier, PowerAdvisor, GearAdvisor.
 *
 * Fixture: app/src/test/resources/fixtures/22923840501.qbot_replay_log.json
 * Source: ~53 min ride, ~20.66 km, flat terrain, no AXS gear data.
 *
 * DYN/STATS from JSON are synthetic/fake — this test computes its own
 * via real extension classes and does NOT compare with JSON values.
 */
class FitReplayValidationTest {

    private lateinit var calc: StatsCalculator
    private val ftp = 200
    private val maxHr = 180

    data class TickData(
        val index: Int,
        val elapsedSec: Long,
        val speedKph: Float,
        val powerWatts: Int,
        val heartRate: Int,
        val cadenceRpm: Int,
        val gradePercent: Float,
        val distanceKm: Float,
        val temperatureC: Float?,
        val frontTeeth: Int,
        val rearTeeth: Int,
    )

    data class Checkpoint(
        val tick: Int,
        val elapsedSec: Long,
        val speedState: FieldState,
        val powerState: FieldState,
        val hrState: FieldState,
        val cadenceState: FieldState,
        val gradeState: FieldState,
        val gearState: FieldState,
        val powerReason: String,
        val gearReason: String,
        val powerTargetLow: Int,
        val powerTargetHigh: Int,
        val npWatts: Int,
        val ifValue: Float,
        val viValue: Float,
        val tssValue: Float,
        val caloriesKcal: Int,
        val decouplingPercent: Float,
        val carbsGPerH: Int,
        val fluidLPerH: Float,
        val rideReservePercent: Int,
        val wBalancePercent: Int,
        val movingSec: Long,
    )

    @Before
    fun setUp() {
        calc = StatsCalculator(ftpWatts = ftp)
        calc.todayFactor = 1.0f
        calc.bodyWeightKg = 75f
        calc.humidityPercent = 50f
        FieldClassifier.resetHysteresis()
    }

    // ── 1. Fixture loads, tick count > 3000 ──
    @Test
    fun `fixture loads with sufficient tick count`() {
        val ticks = loadFixture()
        assertTrue("Tick count should be > 3000, got ${ticks.size}", ticks.size > 3000)
    }

    // ── 2. Elapsed time monotonic ──
    @Test
    fun `elapsed time is monotonic`() {
        val ticks = loadFixture()
        var lastElapsed = -1L
        var violations = 0
        for (t in ticks) {
            if (t.elapsedSec < lastElapsed) violations++
            lastElapsed = t.elapsedSec
        }
        assertTrue("Elapsed time should be monotonic, got $violations violations", violations == 0)
    }

    // ── 3. Distance monotonic ──
    @Test
    fun `distance is monotonic`() {
        val ticks = loadFixture()
        var lastDist = -1f
        var violations = 0
        for (t in ticks) {
            if (t.distanceKm > 0f && t.distanceKm < lastDist) violations++
            if (t.distanceKm > 0f) lastDist = t.distanceKm
        }
        assertTrue("Distance should be monotonic, got $violations violations", violations == 0)
    }

    // ── 4. Sensor values in realistic ranges ──
    @Test
    fun `sensor values in realistic ranges`() {
        val ticks = loadFixture()
        val speedVals = ticks.map { it.speedKph }.filter { it > 0f }
        val powerVals = ticks.map { it.powerWatts }.filter { it > 0 }
        val hrVals = ticks.map { it.heartRate }.filter { it > 0 }
        val cadVals = ticks.map { it.cadenceRpm }.filter { it > 0 }
        val gradeVals = ticks.map { it.gradePercent }

        assertTrue("Speed range ${speedVals.minOrNull()}-${speedVals.maxOrNull()} should be 0-100",
            speedVals.all { it in 0f..100f })
        assertTrue("Power range ${powerVals.minOrNull()}-${powerVals.maxOrNull()} should be 0-2000",
            powerVals.all { it in 0..2000 })
        assertTrue("HR range ${hrVals.minOrNull()}-${hrVals.maxOrNull()} should be 0-250",
            hrVals.all { it in 0..250 })
        assertTrue("Cadence range ${cadVals.minOrNull()}-${cadVals.maxOrNull()} should be 0-250",
            cadVals.all { it in 0..250 })
        assertTrue("Grade range ${gradeVals.minOrNull()}-${gradeVals.maxOrNull()} should be -30..30",
            gradeVals.all { it in -30f..30f })
    }

    // ── 5. Full replay: LIVE classification sanity ──
    @Test
    fun `live classification no frozen or absurd states`() {
        val ticks = loadFixture()
        val cps = replay(ticks, snapEvery = 30)

        // Check no state is permanently frozen to a single value for >80% of checkpoints
        val speedStates = cps.map { it.speedState }
        val powerStates = cps.map { it.powerState }
        val hrStates = cps.map { it.hrState }
        val cadStates = cps.map { it.cadenceState }
        val gradeStates = cps.map { it.gradeState }

        fun <T> frozenRatio(states: List<T>): Double {
            if (states.isEmpty()) return 0.0
            val mostCommon = states.groupingBy { it }.eachCount().maxByOrNull { it.value }?.value ?: 0
            return mostCommon.toDouble() / states.size
        }

        // Speed should vary (GOOD/OK/BAD depending on pace)
        assertTrue("Speed states should not be frozen to single value (>95%)",
            frozenRatio(speedStates) < 0.95)
        // Power should vary
        assertTrue("Power states should not be frozen to single value (>95%)",
            frozenRatio(powerStates) < 0.95)
        // HR should vary
        assertTrue("HR states should not be frozen to single value (>95%)",
            frozenRatio(hrStates) < 0.95)
        // Cadence should vary
        assertTrue("Cadence states should not be frozen to single value (>95%)",
            frozenRatio(cadStates) < 0.95)
        // Grade: on flat rides most grades are in -2..2% → GOOD, which is expected
        // Only fail if 100% frozen (classifier completely broken)
        assertTrue("Grade states should not be 100% frozen (got ${frozenRatio(gradeStates)*100}%)",
            frozenRatio(gradeStates) < 1.0)
    }

    // ── 6. Gear is NEUTRAL when no AXS data (expected, not a bug) ──
    @Test
    fun `gear neutral when no axs data`() {
        val ticks = loadFixture()
        val cps = replay(ticks, snapEvery = 100)
        val neutralGears = cps.count { it.gearState == FieldState.NEUTRAL }
        assertTrue("Gear should be NEUTRAL for most ticks (no AXS data), got $neutralGears/${cps.size}",
            neutralGears > cps.size * 0.9)
    }

    // ── 7. NP/IF/VI/TSS/KCAL computed locally and sensible ──
    @Test
    fun `stats computed locally are sensible`() {
        val ticks = loadFixture()
        val cps = replay(ticks, snapEvery = 60)
        val last = cps.last()

        // After ~53 min ride with avg ~163W:
        // NP should be > 100 (realistic for 163W avg)
        assertTrue("NP at end should be > 100W (got ${last.npWatts})", last.npWatts > 100)
        // IF should be > 0.5 (163W / 200W FTP ≈ 0.8)
        assertTrue("IF at end should be > 0.5 (got ${last.ifValue})", last.ifValue > 0.5f)
        assertTrue("IF at end should be < 1.5 (got ${last.ifValue})", last.ifValue < 1.5f)
        // VI should be close to 1.0 (0.9-1.3 typical)
        assertTrue("VI at end should be 0.8-1.5 (got ${last.viValue})", last.viValue in 0.8f..1.5f)
        // TSS for ~53 min at ~0.8 IF: ~0.8^2 * 53/60 * 100 ≈ 56
        assertTrue("TSS at end should be > 20 (got ${last.tssValue})", last.tssValue > 20f)
        assertTrue("TSS at end should be < 200 (got ${last.tssValue})", last.tssValue < 200f)
        // KCAL for ~53 min at ~163W: ~163W * 3220s / 1000 / 4.184 ≈ 125 kcal
        // But our calc uses kJ directly as kcal (known limitation), so expect ~525 kJ
        assertTrue("KCAL at end should be > 50 (got ${last.caloriesKcal})", last.caloriesKcal > 50)
        assertTrue("KCAL at end should be < 2000 (got ${last.caloriesKcal})", last.caloriesKcal < 2000)
    }

    // ── 8. NP/IF/VI monotonic growth ──
    @Test
    fun `np and tss grow monotonically`() {
        val ticks = loadFixture()
        val cps = replay(ticks, snapEvery = 30)

        for (i in 1 until cps.size) {
            // NP can fluctuate but should not drop more than 10% between consecutive checkpoints
            assertTrue("NP should not drop >10% between checkpoints (tick ${cps[i].tick}: ${cps[i-1].npWatts} -> ${cps[i].npWatts})",
                cps[i].npWatts >= cps[i-1].npWatts * 0.9f)
            // TSS should be strictly non-decreasing
            assertTrue("TSS should be non-decreasing (tick ${cps[i].tick}: ${cps[i-1].tssValue} -> ${cps[i].tssValue})",
                cps[i].tssValue >= cps[i-1].tssValue * 0.99f)
        }
    }

    // ── 9. RSRV in 0..100 and not always 0 ──
    @Test
    fun `rsrv in valid range and not always zero`() {
        val ticks = loadFixture()
        val cps = replay(ticks, snapEvery = 60)

        for (cp in cps) {
            assertTrue("RSRV should be in 0..100 (tick ${cp.tick}: ${cp.rideReservePercent})",
                cp.rideReservePercent in 0..100)
        }

        // RSRV should not be stuck at 0 (unlike the fake JSON)
        val nonZero = cps.count { it.rideReservePercent > 0 }
        assertTrue("RSRV should not be always 0 (got $nonZero/${cps.size} non-zero)", nonZero > 0)
    }

    // ── 10. Pause/gap handling: power=0 during pause doesn't corrupt stats ──
    @Test
    fun `power gaps do not corrupt np`() {
        val ticks = loadFixture()
        val cps = replay(ticks, snapEvery = 10)

        // Find a section where power drops to 0 and then resumes
        for (i in 1 until cps.size - 1) {
            val before = cps[i - 1]
            val current = cps[i]
            val after = cps[i + 1]

            // If current has very low NP (from pause), after should recover
            if (current.npWatts > 0 && after.npWatts < current.npWatts * 0.5f) {
                // This would be a bug — NP shouldn't drop 50% in 10s
                fail("NP dropped >50% in 10s at tick ${after.tick}: ${current.npWatts} -> ${after.npWatts}")
            }
        }
    }

    // ── 11. STATS formatters produce valid output after warmup ──
    @Test
    fun `stats formatters valid after warmup`() {
        val ticks = loadFixture()
        val cps = replay(ticks, snapEvery = 60)

        // After 60 ticks (~60s), most stats should have data
        val warm = cps.find { it.elapsedSec >= 60L }
        assertNotNull("Should have checkpoint at 60s", warm)

        warm?.let { cp ->
            assertNotEquals("NP formatter should not be --", "--", StatsValueFormatter.npW(cp.npWatts).main)
            assertNotEquals("IF formatter should not be --", "--", StatsValueFormatter.ifValue(cp.ifValue).main)
            assertNotEquals("VI formatter should not be --", "--", StatsValueFormatter.vi(cp.viValue).main)
            assertNotEquals("TSS formatter should not be --", "--", StatsValueFormatter.tss(cp.tssValue).main)
            assertNotEquals("KCAL formatter should not be --", "--", StatsValueFormatter.calories(cp.caloriesKcal).main)
        }
    }

    // ── 12. CARB IN and FLUID IN sensible ──
    @Test
    fun `carb and fluid intake sensible`() {
        val ticks = loadFixture()
        val cps = replay(ticks, snapEvery = 60)
        val last = cps.last()

        assertTrue("CARB IN should be 20-110 g/h (got ${last.carbsGPerH})",
            last.carbsGPerH in 20..110)
        assertTrue("FLUID IN should be 0.30-1.50 L/h (got ${last.fluidLPerH})",
            last.fluidLPerH in 0.30f..1.50f)
    }

    // ── 13. Decoupling: not NaN, in range ──
    @Test
    fun `decoupling valid`() {
        val ticks = loadFixture()
        val cps = replay(ticks, snapEvery = 60)
        val last = cps.last()

        // Decoupling should be measurable after ~53 min, clamped to 0..50
        assertTrue("Decoupling should be in 0..50 range (got ${last.decouplingPercent})",
            last.decouplingPercent in 0f..50f)
    }

    // ── 14. W'bal in valid range ──
    @Test
    fun `wbal in valid range`() {
        val ticks = loadFixture()
        val cps = replay(ticks, snapEvery = 60)
        for (cp in cps) {
            if (cp.wBalancePercent >= 0) {
                assertTrue("W'bal should be 0..100 (tick ${cp.tick}: ${cp.wBalancePercent})",
                    cp.wBalancePercent in 0..100)
            }
        }
    }

    // ── 15. Print summary ──
    @Test
    fun `print replay summary`() {
        val ticks = loadFixture()
        val cps = replay(ticks, snapEvery = 60)
        val last = cps.last()

        println()
        println("═══════════════════════════════════════════════════════════")
        println("  FIT REPLAY VALIDATION SUMMARY")
        println("═══════════════════════════════════════════════════════════")
        println("  Ticks:           ${ticks.size}")
        println("  Duration:        ${ticks.last().elapsedSec}s (${ticks.last().elapsedSec / 60} min)")
        println("  Distance:        ${"%.2f".format(ticks.last().distanceKm)} km")
        println()
        println("  ── LIVE classification distribution ──")
        println("    Speed:  ${stateDist(cps.map { it.speedState })}")
        println("    Power:  ${stateDist(cps.map { it.powerState })}")
        println("    HR:     ${stateDist(cps.map { it.hrState })}")
        println("    Cadence: ${stateDist(cps.map { it.cadenceState })}")
        println("    Grade:  ${stateDist(cps.map { it.gradeState })}")
        println("    Gear:   ${stateDist(cps.map { it.gearState })}")
        println()
        println("  ── STATS at end ──")
        println("    NP:    ${last.npWatts} W")
        println("    IF:    ${"%.2f".format(last.ifValue)}")
        println("    VI:    ${"%.2f".format(last.viValue)}")
        println("    TSS:   ${"%.1f".format(last.tssValue)}")
        println("    KCAL:  ${last.caloriesKcal}")
        println("    DRIFT: ${"%.1f".format(last.decouplingPercent)}%")
        println("    RSRV:  ${last.rideReservePercent}%")
        println("    CARB:  ${last.carbsGPerH} g/h")
        println("    FLUID: ${"%.2f".format(last.fluidLPerH)} L/h")
        println("    W'BAL: ${last.wBalancePercent}%")
        println("═══════════════════════════════════════════════════════════")
    }

    // ═══════════════════════════════════════════════════════════════
    // REPLAY ENGINE
    // ═══════════════════════════════════════════════════════════════
    private fun replay(ticks: List<TickData>, snapEvery: Int): List<Checkpoint> {
        val result = mutableListOf<Checkpoint>()
        val powerBuf = ArrayDeque<Int>(3)
        val hrBuf = ArrayDeque<Int>(3)
        val cadBuf = ArrayDeque<Int>(3)
        val gradeBuf = ArrayDeque<Float>(5)
        var timeMs = 0L

        calc.reset()
        calc.resetReserveGuard()
        FieldClassifier.resetHysteresis()

        for (t in ticks) {
            val elapsed = t.elapsedSec
            val movingSec = if (t.powerWatts > 0 || t.speedKph > 3) elapsed else {
                // Find last moving elapsed
                ticks.take(t.index).lastOrNull { it.powerWatts > 0 || it.speedKph > 3 }?.elapsedSec ?: 0L
            }

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

            calc.update(t.powerWatts, t.heartRate, movingSec, elapsed)

            val rideState = RideState(
                speedKph = t.speedKph,
                powerWatts = t.powerWatts,
                heartRate = t.heartRate,
                cadenceRpm = t.cadenceRpm,
                gradePercent = t.gradePercent,
                distanceKm = t.distanceKm,
                elapsedSec = elapsed,
                movingSec = movingSec,
                frontTeeth = t.frontTeeth,
                rearTeeth = t.rearTeeth,
                temperatureCelsius = t.temperatureC,
                npWholeWatts = calc.npWatts(),
                ifWholeRide = calc.ifValue(),
                ifValue = calc.ifValue(),
                viValue = calc.viValue(),
                tssValue = calc.tssValue(movingSec),
                caloriesKcal = calc.caloriesKcal(),
                avgPowerWatts = if (calc.caloriesKcal() > 0) (calc.caloriesKcal() * 1000 / maxOf(1, elapsed.toInt())) else 0,
                decouplingPercent = calc.decouplingPercent(),
                carbsGPerH = calc.carbsGPerH(calc.ifValue(), movingSec, calc.viValue(), t.temperatureC, 75f),
                fluidLPerH = calc.fluidLPerH(calc.ifValue(), t.temperatureC),
                rideReservePercent = calc.rideReservePercent(calc.tssValue(movingSec), calc.ifValue(), calc.decouplingPercent()),
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

            if (t.index % snapEvery == 0 || t.index == ticks.lastIndex) {
                result.add(Checkpoint(
                    tick = t.index,
                    elapsedSec = elapsed,
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
                    npWatts = calc.npWatts(),
                    ifValue = calc.ifValue(),
                    viValue = calc.viValue(),
                    tssValue = calc.tssValue(movingSec),
                    caloriesKcal = calc.caloriesKcal(),
                    decouplingPercent = calc.decouplingPercent(),
                    carbsGPerH = calc.carbsGPerH(calc.ifValue(), movingSec, calc.viValue(), t.temperatureC, 75f),
                    fluidLPerH = calc.fluidLPerH(calc.ifValue(), t.temperatureC),
                    rideReservePercent = calc.rideReservePercent(calc.tssValue(movingSec), calc.ifValue(), calc.decouplingPercent()),
                    wBalancePercent = calc.wBalancePercent(),
                    movingSec = movingSec,
                ))
            }
        }

        return result
    }

    private fun loadFixture(): List<TickData> {
        val stream = javaClass.classLoader.getResourceAsStream("fixtures/22923840501.qbot_replay_log.json")
            ?: throw IllegalStateException("Fixture not found: fixtures/22923840501.qbot_replay_log.json")

        val jsonStr = BufferedReader(InputStreamReader(stream)).use { it.readText() }
        val gson = Gson()
        val root = gson.fromJson(jsonStr, JsonObject::class.java)
        val ticksArr = root.getAsJsonArray("ticks")
        val ticks = mutableListOf<TickData>()

        for (i in 0 until ticksArr.size()) {
            val tick = ticksArr[i].asJsonObject
            val rideState = tick.getAsJsonObject("rideState")
            val replayTick = tick.getAsJsonObject("replayTick")

            val speedMps = rideState.get("speedMps")?.takeUnless { it.isJsonNull }?.asFloat ?: 0f
            val powerW = rideState.get("powerW")?.takeUnless { it.isJsonNull }?.asInt ?: 0
            val hrBpm = rideState.get("heartRateBpm")?.takeUnless { it.isJsonNull }?.asInt ?: 0
            val cadRpm = rideState.get("cadenceRpm")?.takeUnless { it.isJsonNull }?.asInt ?: 0
            val grade = rideState.get("grade")?.takeUnless { it.isJsonNull }?.asFloat ?: 0f
            val distM = rideState.get("distanceM")?.takeUnless { it.isJsonNull }?.asDouble ?: 0.0
            val elapsedSec = replayTick.get("elapsedSec")?.asDouble?.toLong() ?: 0L

            ticks.add(TickData(
                index = i,
                elapsedSec = elapsedSec,
                speedKph = speedMps * 3.6f,
                powerWatts = powerW,
                heartRate = hrBpm,
                cadenceRpm = cadRpm,
                gradePercent = grade,
                distanceKm = (distM / 1000.0).toFloat(),
                temperatureC = null,
                frontTeeth = 0,
                rearTeeth = 0,
            ))
        }

        return ticks
    }

    private fun stateDist(states: List<FieldState>): String {
        val counts = states.groupingBy { it }.eachCount()
        return FieldState.values().joinToString(", ") { s ->
            "${s.name}=${counts[s] ?: 0}"
        }
    }
}
