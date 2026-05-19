package com.bikepacking.karoo.fitexport

import kotlin.math.*
import kotlin.random.Random

/**
 * Simplified CLI RideEngine that processes ReplayTick data through
 * the same algorithms as the Android RideEngine.
 *
 * Source of truth for all algorithms:
 *   karoo-final/app/src/main/kotlin/com/bikepacking/karoo/
 *     StatsCalculator.kt
 *     field/PowerAdvisor.kt
 *     field/GearAdvisor.kt
 *     field/FieldClassifier.kt
 *     field/TodayProfile.kt
 *     RideEngine.kt (updateState logic)
 *
 * THIS IS A FAITHFUL PORT FOR CLI USE ONLY.
 * Do NOT treat this as the source of truth for QBot logic.
 * Always validate against the Kotlin RideEngine in karoo-final.
 */
class CliRideEngine(
    val ftpWatts: Int = 200,
    val maxHr: Int = 175,
    val todayFactor: Float = 0.95f,
    val wPrimeKj: Float = 20.0f,
    val ltpWatts: Float = 180f,
    val bodyWeightKg: Float = 75f,
) {
    // ── Rolling averages ────────────────────────────────────────
    private val powerBuffer3s = RollingWindow(3)
    private val hrBuffer3s = RollingWindow(3)
    private val cadBuffer3s = RollingWindow(3)
    private val gradeBuffer = RollingWindow(5)

    // ── Stats accumulators (mirrors StatsCalculator.kt) ─────────
    private val powerBuffer30s = ArrayDeque<Int>(30)
    private var sumOf4thPowers = 0.0
    private var count4thPowers = 0L
    private var totalPowerSum = 0L
    private var totalPowerCount = 0L
    private var totalEnergyKj = 0.0
    private val decoupleHr = ArrayDeque<Int>(60)
    private val decouplePower = ArrayDeque<Int>(60)
    private var lastMovingSec = 0L
    private var wBalKj = wPrimeKj
    private var lastReserve = 100

    // ── Hysteresis (mirrors FieldClassifier.kt) ─────────────────
    private var lastHrState = 2       // 0=GOOD..4=BAD, 2=OK
    private var hrStateSinceMs = 0L
    private var hrInitialized = false
    private var lastGearState = 2
    private var gearStateSinceMs = 0L
    private var gearInitialized = false
    private val HR_HYSTERESIS_MS = 15_000L
    private val GEAR_HYSTERESIS_MS = 30_000L

    // ── Running totals ──────────────────────────────────────────
    var elapsedSec = 0L; private set
    var movingSec = 0L; private set
    var distanceKm = 0.0; private set
    var npWatts = 0; private set
    var ifValue = 0.0; private set
    var viValue = 0.0; private set
    var tssValue = 0.0; private set
    var caloriesKcal = 0; private set
    var decouplingPercent: Double? = null; private set
    var carbsGPerH = 0; private set
    var fluidLPerH = 0.0; private set
    var rideReservePercent = 100; private set
    var wBalPercent = 100; private set
    var if10 = 0.0; private set
    var np10 = 0; private set
    var avgNetKph = 0.0; private set
    var avgGrossKph = 0.0; private set
    var ascentDoneM = 0.0; private set

    // ── Sensor presence flags ───────────────────────────────────
    var hasPowerData = false; private set
    var hasHeartRateData = false; private set
    var hasCadenceData = false; private set
    var hasGearData = false; private set

    // ── Battery simulation ──────────────────────────────────────
    var batteryPercent = 85.0; private set
    private var batteryStartTime = 0L
    private var batteryStartPct = 85.0
    var batteryDropPerHour = 0.0; private set
    var batteryRuntimeSec = 0L; private set
    var rearDerailleurBatteryPercent: Int? = 83; private set

    // ── Optional RNG for battery simulation reproducibility ─────
    private val rng = Random(42)

    fun reset() {
        powerBuffer3s.clear()
        hrBuffer3s.clear()
        cadBuffer3s.clear()
        gradeBuffer.clear()
        powerBuffer30s.clear()
        sumOf4thPowers = 0.0
        count4thPowers = 0L
        totalPowerSum = 0L
        totalPowerCount = 0L
        totalEnergyKj = 0.0
        decoupleHr.clear()
        decouplePower.clear()
        lastMovingSec = 0L
        wBalKj = wPrimeKj
        lastReserve = 100
        lastHrState = 2
        hrStateSinceMs = 0L
        hrInitialized = false
        lastGearState = 2
        gearStateSinceMs = 0L
        gearInitialized = false
        elapsedSec = 0L
        movingSec = 0L
        distanceKm = 0.0
        npWatts = 0
        ifValue = 0.0
        viValue = 0.0
        tssValue = 0.0
        caloriesKcal = 0
        decouplingPercent = null
        carbsGPerH = 0
        fluidLPerH = 0.0
        rideReservePercent = 100
        wBalPercent = 100
        if10 = 0.0
        np10 = 0
        avgNetKph = 0.0
        avgGrossKph = 0.0
        ascentDoneM = 0.0
        hasPowerData = false
        hasHeartRateData = false
        hasCadenceData = false
        hasGearData = false
        batteryPercent = 85.0
        batteryStartTime = 0L
        batteryStartPct = 85.0
        batteryDropPerHour = 0.0
        batteryRuntimeSec = 0L
    }

    fun process(tick: ReplayTick, tickIndex: Int): EngineResult {
        val ts = tick.timestampMs
        val power = tick.powerW ?: 0
        val hr = tick.heartRateBpm ?: 0
        val cad = tick.cadenceRpm ?: 0
        val grade = tick.gradePct ?: 0.0
        val speedKph = (tick.speedMps ?: 0.0) * 3.6
        val dist = tick.distanceM ?: 0.0
        val alt = tick.altitudeM
        val tempC = tick.temperatureC

        if (power > 0) hasPowerData = true
        if (hr > 0) hasHeartRateData = true
        if (cad > 0) hasCadenceData = true
        if (tick.gear != null) hasGearData = true

        // ── State updates ────────────────────────────────────────
        elapsedSec = if (ts > 0) ts / 1000 else tickIndex.toLong()
        val isMoving = power > 0 || speedKph > 3.0
        if (isMoving) movingSec = elapsedSec
        distanceKm = dist / 1000.0

        // Rolling averages (3-second windows)
        powerBuffer3s.push(power.toDouble())
        hrBuffer3s.push(hr.toDouble())
        cadBuffer3s.push(cad.toDouble())
        gradeBuffer.push(grade)

        val smoothedPower = powerBuffer3s.average().roundToInt()
        val smoothedHr = hrBuffer3s.average().roundToInt()
        val smoothedCad = cadBuffer3s.average().roundToInt()
        val smoothedGrade = gradeBuffer.average()

        // ── Stats calculation (StatsCalculator.kt) ───────────────
        val movingAdvanced = movingSec > lastMovingSec
        val isActivePowerSample = movingAdvanced && power > 0

        if (isActivePowerSample) {
            powerBuffer30s.addLast(power)
            if (powerBuffer30s.size > 30) powerBuffer30s.removeFirst()
            if (powerBuffer30s.size == 30) {
                val avg30s = powerBuffer30s.average()
                sumOf4thPowers += avg30s.pow(4)
                count4thPowers++
            }
            totalPowerSum += power.toLong()
            totalPowerCount++
            totalEnergyKj += power / 1000.0
        }

        if (isActivePowerSample && hr > 0) {
            decoupleHr.addLast(hr)
            decouplePower.addLast(power)
            if (decoupleHr.size > 60) decoupleHr.removeFirst()
            if (decouplePower.size > 60) decouplePower.removeFirst()
        }

        // W' Balance (Skiba model)
        if (ltpWatts > 0 && wPrimeKj > 0) {
            if (power > ltpWatts) {
                wBalKj -= (power - ltpWatts) * 1f / 1000f
            } else if (wBalKj < wPrimeKj) {
                val tau = 546f
                wBalKj += (wPrimeKj - wBalKj) * (1f - exp(-1f / tau))
            }
            wBalKj = wBalKj.coerceIn(0f, wPrimeKj)
            wBalPercent = ((wBalKj / wPrimeKj) * 100f).roundToInt().coerceIn(0, 100)
        }

        lastMovingSec = maxOf(lastMovingSec, movingSec)

        // ── NP, IF, VI, TSS ──────────────────────────────────────
        if (count4thPowers > 0) {
            npWatts = (sumOf4thPowers / count4thPowers).pow(0.25).roundToInt()
        }
        ifValue = if (ftpWatts > 0) (npWatts.toFloat() / ftpWatts).coerceAtMost(2.0).toDouble() else 0.0

        if (totalPowerCount > 0) {
            val avgPwr = totalPowerSum.toDouble() / totalPowerCount
            if (avgPwr > 0) viValue = (npWatts.toDouble() / avgPwr).coerceAtMost(2.0)
        }

        // TSS: uses moving time
        if (ftpWatts > 0 && movingSec > 0 && npWatts > 0 && ifValue > 0) {
            tssValue = ((movingSec.toDouble() * npWatts * ifValue) / (ftpWatts * 3600.0) * 100.0)
                .coerceIn(0.0, 9999.0)
        }

        // ── Calories ──────────────────────────────────────────────
        caloriesKcal = totalEnergyKj.roundToInt()

        // ── Decoupling (DRIFT) ────────────────────────────────────
        if (decoupleHr.size >= 60) {
            val firstHr = decoupleHr.take(30).average()
            val firstPwr = decouplePower.take(30).average()
            val secondHr = decoupleHr.drop(30).take(30).average()
            val secondPwr = decouplePower.drop(30).take(30).average()
            if (firstPwr > 0.0 && secondPwr > 0.0) {
                val r1 = firstHr / firstPwr
                val r2 = secondHr / secondPwr
                if (r1 > 0.0) {
                    decouplingPercent = (((r2 - r1) / r1) * 100.0).coerceIn(-20.0, 50.0)
                }
            }
        }

        // ── CARB IN ───────────────────────────────────────────────
        carbsGPerH = calcCarbs(tempC)

        // ── FLUID IN ──────────────────────────────────────────────
        fluidLPerH = calcFluid(tempC)

        // ── RSRV ──────────────────────────────────────────────────
        rideReservePercent = calcRsrv()

        // ── DYN accumulators ──────────────────────────────────────
        if10 = ifValue  // simplified: current IF as IF10
        np10 = npWatts
        avgNetKph = if (movingSec > 0) distanceKm / (movingSec / 3600.0) else 0.0
        avgGrossKph = if (elapsedSec > 0) distanceKm / (elapsedSec / 3600.0) else 0.0

        // Ascent
        if (alt != null && alt > 0) {
            val baseAlt = 100.0
            ascentDoneM = max(0.0, alt - baseAlt)
        }

        // ── Battery simulation ────────────────────────────────────
        calcBattery(ts)

        // ── Field classification ──────────────────────────────────
        val avgSpeedKph = avgNetKph
        val speedState = classifySpeed(speedKph, avgSpeedKph)
        val powerResult = assessPower(smoothedPower, smoothedGrade)
        val hrState = classifyHr(smoothedHr, ts)
        val cadState = classifyCadence(smoothedCad, smoothedGrade)
        val gradeState = classifyGrade(smoothedGrade)
        val gearResult = assessGear(smoothedCad, smoothedPower, smoothedGrade, ts)

        // ── Build snapshots ───────────────────────────────────────
        val fields = HudFields(
            speed = FieldSnapshot(speedState.first, formatSpeed(speedKph), speedState.second),
            power = PowerFieldSnapshot(
                powerResult.first, formatPower(powerResult.third),
                powerResult.second, powerResult.fourth, powerResult.fifth,
            ),
            hr = FieldSnapshot(hrState.first, formatHr(smoothedHr), hrState.second),
            cadence = FieldSnapshot(cadState.first, formatCad(smoothedCad), cadState.second),
            grade = FieldSnapshot(gradeState.first, formatGrade(smoothedGrade), gradeState.second),
            gear = FieldSnapshot(gearResult.first, formatGear(tick), gearResult.second),
        )

        val dyn = DynSnapshot(
            ifNp = DynIfNp(
                ifValue.let { if (it > 0) "IF ${String.format("%.2f", it)}" else "IF .--" },
                npWatts.let { if (it > 0) "NP $it" else "NP ---" },
            ),
            temp = DynTemp(
                tempC?.let { "${it.roundToInt()}°" } ?: "--°",
                if (tempC != null) {
                    when {
                        tempC < 5 -> "cold"
                        tempC < 20 -> ""
                        tempC < 30 -> "warm"
                        else -> "hot"
                    }
                } else "",
            ),
            wind = DynWind("-", 0.0),
            avg = DynAvg(
                if (avgNetKph > 0) String.format("%.1f", avgNetKph) else "--",
                if (avgGrossKph > 0) String.format("%.1f", avgGrossKph) else "--",
            ),
            dist = DynDist(
                if (distanceKm > 0) String.format("%.1f", distanceKm) else "0.0",
                if (tick.route?.distanceToDestinationM != null)
                    String.format("%.1f", tick.route!!.distanceToDestinationM!! / 1000)
                else "--",
            ),
            eta = DynEta("--:--"),
        )

        val stats = mapOf<String, Any?>(
            "np" to npWatts,
            "if" to ifValue,
            "vi" to viValue,
            "tss" to tssValue,
            "calories" to caloriesKcal,
            "decoupling" to decouplingPercent,
            "carbsGPerH" to carbsGPerH,
            "fluidLPerH" to fluidLPerH,
            "rideReservePercent" to rideReservePercent,
            "wBalPercent" to wBalPercent,
            "ascentDoneM" to ascentDoneM,
            "avgPower" to (if (totalPowerCount > 0) (totalPowerSum / totalPowerCount).toInt() else 0),
        )

        val hudState = HudStateSnapshot(
            tick = tickIndex,
            fields = fields,
            dyn = dyn,
            stats = stats,
            message = null,
        )

        val rideState = RideStateSnapshot(
            tick = tickIndex,
            timestampMs = ts,
            elapsedSec = elapsedSec,
            movingSec = movingSec,
            distanceKm = Math.round(distanceKm * 100.0) / 100.0,
            speedKph = Math.round(speedKph * 10.0) / 10.0,
            powerWatts = power,
            heartRate = hr,
            cadenceRpm = cad,
            gradePercent = Math.round(grade * 10.0) / 10.0,
            npWatts = npWatts,
            ifValue = Math.round(ifValue * 100.0) / 100.0,
            viValue = Math.round(viValue * 100.0) / 100.0,
            tssValue = Math.round(tssValue),
            caloriesKcal = caloriesKcal,
            decouplingPercent = decouplingPercent?.let { Math.round(it * 10.0) / 10.0 },
            carbsGPerH = carbsGPerH,
            fluidLPerH = Math.round(fluidLPerH * 100.0) / 100.0,
            rideReservePercent = rideReservePercent,
            wBalPercent = wBalPercent,
            batteryPercent = batteryPercent.roundToInt(),
            batteryDropPerHour = Math.round(batteryDropPerHour * 10.0) / 10.0,
            batteryRuntimeSec = batteryRuntimeSec,
            rearDerailleurBatteryPercent = rearDerailleurBatteryPercent,
        )

        return EngineResult(hudState, rideState)
    }

    // ── CARB IN (mirrors StatsCalculator.kt:carbsGPerH) ────────
    private fun calcCarbs(tempC: Double?): Int {
        val ifClamped = if10.coerceIn(0.4, 1.1).toFloat()
        var carbs = 25f + ((ifClamped - 0.4f) / 0.7f) * 65f
        val movingHours = movingSec / 3600f
        val durMult = when {
            movingHours < 1f -> 1.0f
            movingHours < 2f -> 1.08f
            movingHours < 3f -> 1.15f
            else -> 1.22f
        }
        val viMult = when {
            viValue <= 1.05 -> 1.0f
            viValue <= 1.12 -> 1.05f
            else -> 1.10f
        }
        val wtMult = (bodyWeightKg / 75f).coerceIn(0.85f, 1.20f)
        val tempMult = when {
            tempC == null -> 1.0f
            tempC < 5f -> 0.95f
            tempC < 25f -> 1.0f
            tempC < 32f -> 1.05f
            else -> 1.08f
        }
        val result = carbs * durMult * viMult * wtMult * tempMult
        return (round(result / 5f) * 5f).toInt().coerceIn(20, 110)
    }

    // ── FLUID IN (mirrors StatsCalculator.kt:fluidLPerH) ───────
    private fun calcFluid(tempC: Double?): Double {
        val z = if10.toFloat()
        val base = when { z < 0.55f -> 0.40f; z < 0.75f -> 0.50f; z < 0.87f -> 0.60f; else -> 0.70f }
        val tm = when {
            tempC == null -> 1.0f
            tempC < 5f -> 0.75f
            tempC < 12f -> 0.85f
            tempC < 18f -> 0.95f
            tempC < 24f -> 1.10f
            tempC < 30f -> 1.30f
            tempC < 35f -> 1.50f
            else -> 1.70f
        }
        val hm = 1.0f  // assume 50% humidity (no FIT field)
        val result = base * tm * hm * (bodyWeightKg / 70f)
        return (round(result / 0.05f) * 0.05f).toDouble().coerceIn(0.30, 1.50)
    }

    // ── RSRV (mirrors StatsCalculator.kt:rideReservePercent) ────
    private fun calcRsrv(): Int {
        var reserve = todayFactor * 100f
        if (tssValue > 0) reserve -= tssValue.toFloat() * 0.6f
        if (if10 > 0.75) reserve -= (if10.toFloat() - 0.75f) * 80f
        val decouple = decouplingPercent ?: 0.0
        if (decoupleHr.size >= 60 && decouple > 5.0) reserve -= (decouple.toFloat() - 5f) * 2f
        val result = reserve.roundToInt().coerceIn(0, 100)
        val monotonic = minOf(result, lastReserve)
        lastReserve = monotonic
        return monotonic
    }

    // ── Battery simulation ──────────────────────────────────────
    private fun calcBattery(ts: Long) {
        if (batteryStartTime == 0L) batteryStartTime = ts
        val drainPerSec = 0.0005 + (powerBuffer3s.currentOrLast() / ftpWatts.toDouble()) * 0.001
        batteryPercent = max(5.0, batteryPercent - drainPerSec)

        val elapsedH = if (ts > batteryStartTime) (ts - batteryStartTime) / 3_600_000.0 else 0.0
        if (elapsedH > 0.01) {
            val drop = batteryStartPct - batteryPercent
            batteryDropPerHour = drop / elapsedH
        }
        if (batteryDropPerHour > 0) {
            batteryRuntimeSec = (batteryPercent / batteryDropPerHour * 3600).toLong()
        }

        // Simulate AXS battery (very slow drain)
        if (rearDerailleurBatteryPercent != null && ts > 0 && ts % 60000 == 0L) {
            rearDerailleurBatteryPercent = maxOf(5, rearDerailleurBatteryPercent!! - 1)
        }
    }

    // ── Speed classifier (mirrors FieldClassifier.kt:speed) ─────
    private fun classifySpeed(kph: Double, netAvg: Double): Pair<String, String> {
        if (netAvg < 1.0) return Pair("neutral", "no_ref")
        val ratio = kph / netAvg
        return when {
            ratio > 1.15 -> Pair("good", "above_avg")
            ratio < 0.85 -> Pair("bad", "below_avg")
            else -> Pair("ok", "in_range")
        }
    }

    // ── Power advisor (mirrors PowerAdvisor.kt) ─────────────────
    private fun assessPower(
        smoothedWatts: Int,
        smoothedGrade: Double,
    ): Quad<String, String, Int, Int, Int> {
        if (ftpWatts <= 0 || smoothedWatts <= 0) {
            return Quad("neutral", "no_data", 0, 0, smoothedWatts)
        }
        if (wBalPercent in 0..14) {
            return Quad("bad", "wbal_critical", 0, 0, smoothedWatts)
        }

        val adjustedFtp = (ftpWatts * todayFactor).roundToInt().coerceAtLeast(50)
        val isShortClimb = smoothedGrade > 3.0  // simplified: no ascentLeft check
        val isLongRide = elapsedSec > 14_400L

        val (targetLow, targetHigh) = when {
            isShortClimb -> Pair((adjustedFtp * 0.80).roundToInt(), (adjustedFtp * 1.05).roundToInt())
            isLongRide -> Pair((adjustedFtp * 0.75 * 0.90).roundToInt(), (adjustedFtp * 0.87 * 0.90).roundToInt())
            else -> Pair((adjustedFtp * 0.75).roundToInt(), (adjustedFtp * 0.87).roundToInt())
        }

        val (state, reason) = when {
            smoothedWatts < targetLow -> {
                if (smoothedWatts < (targetLow * 0.80).roundToInt())
                    Pair("bad", "far_below")
                else Pair("warn", "below_target")
            }
            smoothedWatts > targetHigh -> {
                if (smoothedWatts > (targetHigh * 1.20).roundToInt())
                    Pair("bad", "far_above")
                else Pair("warn", "above_target")
            }
            else -> Pair("good", "in_range")
        }

        // Risk modifiers
        var finalState = state
        var finalReason = reason
        var riskMods = 0
        val decouple = decouplingPercent ?: 0.0
        if (decouple > 5.0 && decoupleHr.size >= 60) riskMods++
        if (todayFactor < 0.85f) riskMods++

        repeat(riskMods.coerceAtMost(2)) {
            finalState = worsen(finalState)
            finalReason = "${finalReason}_mod"
        }

        return Quad(finalState, finalReason, targetLow, targetHigh, smoothedWatts)
    }

    // ── HR classifier (mirrors FieldClassifier.kt:hr) ──────────
    private fun classifyHr(bpm: Int, nowMs: Long): Pair<String, String> {
        if (maxHr <= 0 || bpm <= 0) return Pair("neutral", "no_data")
        val pct = bpm.toDouble() / maxHr
        val rawState = when {
            pct < 0.60 -> "ok"
            pct < 0.75 -> "good"
            pct < 0.85 -> "ok"
            pct < 0.95 -> "warn"
            else -> "bad"
        }

        // 15s hysteresis (mirrors FieldClassifier.kt)
        if (!hrInitialized) {
            lastHrState = rawState.ordinal()
            hrStateSinceMs = nowMs
            hrInitialized = true
            return Pair(rawState, "zone")
        }
        if (rawState.ordinal() == lastHrState) {
            hrStateSinceMs = nowMs
            return Pair(rawState, "zone")
        }
        if (nowMs - hrStateSinceMs < HR_HYSTERESIS_MS) {
            return Pair(lastHrState.label(), "zone_hysteresis")
        }
        lastHrState = rawState.ordinal()
        hrStateSinceMs = nowMs
        return Pair(rawState, "zone")
    }

    // ── Cadence classifier (mirrors FieldClassifier.kt:cadence) ─
    private fun classifyCadence(rpm: Int, grade: Double): Pair<String, String> {
        if (rpm <= 0) return Pair("neutral", "no_data")
        val (targetLow, targetHigh) = when {
            grade > 4.0 -> Pair(55, 65)
            grade < -4.0 -> Pair(65, 75)
            else -> Pair(60, 70)
        }
        val okLow = targetLow - 5
        val okHigh = targetHigh + 7
        return when {
            rpm in targetLow..targetHigh -> Pair("good", "in_range")
            rpm in okLow until targetLow || rpm in (targetHigh + 1)..okHigh -> Pair("ok", "near_range")
            else -> Pair("bad", "out_of_range")
        }
    }

    // ── Grade classifier (mirrors FieldClassifier.kt:grade) ────
    private fun classifyGrade(grade: Double): Pair<String, String> {
        return when {
            grade in -2.0..2.0 -> Pair("good", "flat")
            grade in 2.0..5.0 || grade in -5.0..-2.0 -> Pair("ok", "moderate")
            grade in 5.0..9.0 || grade < -5.0 -> Pair("warn", "steep")
            else -> Pair("bad", "very_steep")
        }
    }

    // ── Gear advisor (mirrors GearAdvisor.kt) ───────────────────
    private fun assessGear(
        cad: Int,
        power: Int,
        grade: Double,
        nowMs: Long,
    ): Pair<String, String> {
        if (cad <= 0 || power <= 0) return Pair("neutral", "no_data")
        val adjustedFtp = (ftpWatts * todayFactor).roundToInt().coerceAtLeast(50)
        if (adjustedFtp <= 0) return Pair("neutral", "no_ftp")

        val raw = when {
            cad <= 50 && power >= (adjustedFtp * 1.10).roundToInt() && grade >= 5.0 -> Pair("bad", "too_hard")
            cad < 55 && power >= (adjustedFtp * 0.75).roundToInt() && grade >= 2.0 -> Pair("warn", "likely_too_hard")
            cad >= 90 && power <= (adjustedFtp * 0.50).roundToInt() -> Pair("warn", "too_easy")
            cad in 60..75 && power in (adjustedFtp * 0.75).roundToInt()..(adjustedFtp * 0.87).roundToInt()
                && grade > -5.0 && grade < 5.0 -> Pair("good", "sweet_spot")
            else -> Pair("ok", "acceptable")
        }

        // 30s hysteresis (mirrors FieldClassifier.kt:gear)
        if (!gearInitialized) {
            lastGearState = raw.first.ordinal()
            gearStateSinceMs = nowMs
            gearInitialized = true
            return raw
        }
        if (raw.first.ordinal() == lastGearState) {
            gearStateSinceMs = nowMs
            return raw
        }
        if (nowMs - gearStateSinceMs < GEAR_HYSTERESIS_MS) {
            return Pair(lastGearState.label(), raw.second + "_hysteresis")
        }
        lastGearState = raw.first.ordinal()
        gearStateSinceMs = nowMs
        return raw
    }

    // ── Helpers ─────────────────────────────────────────────────
    private fun worsen(state: String): String = when (state) {
        "good" -> "ok"
        "ok" -> "warn"
        "warn" -> "bad"
        else -> state
    }

    private fun String.ordinal(): Int = when (this) {
        "good" -> 0
        "ok" -> 1
        "warn" -> 2
        "bad" -> 3
        else -> 2  // neutral -> ok
    }

    private fun Int.label(): String = when (this) {
        0 -> "good"
        1 -> "ok"
        2 -> "warn"
        3 -> "bad"
        else -> "neutral"
    }

    companion object {
        private fun Double.roundToInt(): Int = Math.round(this).toInt()
        private fun Float.roundToInt(): Int = Math.round(this)
        private fun round(v: Float, factor: Float): Float = (Math.round(v / factor) * factor)
    }
}

// ── Result types ────────────────────────────────────────────────
data class EngineResult(
    val hudState: HudStateSnapshot,
    val rideState: RideStateSnapshot,
)

data class Quad<A, B, C, D, E>(val first: A, val second: B, val third: C, val fourth: D, val fifth: E)

// ── Simple rolling window for CLI use ───────────────────────────
class RollingWindow(private val size: Int) {
    private val buf = DoubleArray(size)
    private var idx = 0
    private var count = 0

    fun push(v: Double) { buf[idx % size] = v; idx++; if (count < size) count++ }
    fun average(): Double = if (count == 0) 0.0 else buf.take(count).sum() / count
    fun currentOrLast(): Double = if (count == 0) 0.0 else buf[(idx - 1) % size]
    fun clear() { idx = 0; count = 0 }
}

// ── Formatters (mirrors FieldFormatter.kt) ──────────────────────
private fun formatSpeed(kph: Double): String = when {
    kph <= 0 -> "0.0"
    kph >= 100 -> kph.roundToInt().toString()
    else -> String.format("%.1f", kph)
}

private fun formatPower(watts: Int): String = if (watts <= 0) "--" else minOf(999, watts).toString()

private fun formatHr(bpm: Int): String = if (bpm <= 0) "--" else minOf(220, bpm).toString()

private fun formatCad(rpm: Int): String = if (rpm <= 0) "--" else minOf(99, rpm).toString()

private fun formatGrade(grade: Double): String {
    val sign = if (grade >= 0) "+" else ""
    return sign + String.format("%.1f", grade) + "%"
}

private fun formatGear(tick: ReplayTick): String {
    val front = tick.gear?.frontTeeth
    val rear = tick.gear?.rearTeeth
    return if (front != null && rear != null) "${minOf(99, front)}×${minOf(99, rear)}" else "--×--"
}
