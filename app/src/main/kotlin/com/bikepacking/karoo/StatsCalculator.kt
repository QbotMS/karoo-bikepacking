package com.bikepacking.karoo

import kotlin.math.*

class StatsCalculator(var ftpWatts: Int = 200) {

    // ── NP buffer ────────────────────────────────────────────────────────
    private val powerBuffer30s = ArrayDeque<Int>(30)
    private var sumOf4thPowers = 0.0
    private var count4thPowers = 0L

    // ── Avg Power ────────────────────────────────────────────────────────
    private var totalPowerSum = 0L
    private var totalPowerCount = 0L
    private var totalEnergyKj = 0.0

    // ── Decoupling (accumulated active samples) ─────────────────────────────
    private val decoupleHr = mutableListOf<Int>()
    private val decouplePower = mutableListOf<Int>()

    // ── HRD: Local HR Drift/Strain ────────────────────────────────────────
    private val hrdSamples = mutableListOf<HrdSample>()  // (timestampMs, hr, power, cadence)
    private var hrdBaselineRatio: Float? = null
    private var hrdStopStartMs: Long = 0L
    private var hrdWarmupDoneMs: Long = 0L

    data class HrdSample(val timestampMs: Long, val hr: Int, val power: Int, val cadence: Int)

    // ── Readiness ─────────────────────────────────────────────────────────
    var todayFactor: Float = 1.0f
    var ctl: Float = 60f
    var bodyWeightKg: Float = 75f
    var humidityPercent: Float = 50f

    // ── Pause filter ───────────────────────────────────────────────────────
    private var lastMovingSec: Long = 0L

    // ── RSRV smoothing ─────────────────────────────────────────────────────
    private var lastReserve: Float = 100f
    private var startReserve: Float = 100f   // Captured at ride start, never exceeded

    fun captureStartReserve() {
        startReserve = safetyFloat(todayFactor) * 100f
        lastReserve = startReserve
    }

    // ── W' Balance (Skiba model) ──────────────────────────────────────────
    private var wPrimeKj: Float = 21.3f   // hie_kj z Xert
    private var ltpWatts: Float = 192f    // ltp_watts z Xert (Critical Power)
    private var wBalKj: Float = 21.3f
    private val TAU: Float = 546f

    fun setWPrimeParams(wPrime: Float, ltp: Float) {
        if (wPrime > 0f) { wPrimeKj = wPrime; wBalKj = wPrime }
        if (ltp > 0f) ltpWatts = ltp
    }

    private fun updateWBalance(powerWatts: Int) {
        if (ltpWatts <= 0f || wPrimeKj <= 0f) return
        if (powerWatts > ltpWatts) {
            wBalKj -= (powerWatts - ltpWatts) * 1f / 1000f
        } else {
            wBalKj += (wPrimeKj - wBalKj) * (1f - exp(-1f / TAU))
        }
        wBalKj = wBalKj.coerceIn(0f, wPrimeKj)
    }

    fun wBalancePercent(): Int =
        if (wPrimeKj > 0f) ((wBalKj / wPrimeKj) * 100f).roundToInt().coerceIn(0, 100) else -1

    // ── Tick ─────────────────────────────────────────────────────────────
    fun update(powerWatts: Int, heartRate: Int, movingSec: Long, elapsedSec: Long) {
        if (elapsedSec <= 0L) return

        val movingAdvanced = movingSec > lastMovingSec
        val hasPower = powerWatts > 0
        val isActivePowerSample = movingAdvanced && hasPower

        if (isActivePowerSample) {
            powerBuffer30s.addLast(powerWatts)
            if (powerBuffer30s.size > 30) powerBuffer30s.removeFirst()
            if (powerBuffer30s.size == 30) {
                val avg30s = powerBuffer30s.average()
                sumOf4thPowers += avg30s.pow(4)
                count4thPowers++
            }
            totalPowerSum += powerWatts; totalPowerCount++
            totalEnergyKj += powerWatts / 1000.0
        }

        if (isActivePowerSample && heartRate > 0) {
            decoupleHr.add(heartRate)
            decouplePower.add(powerWatts)
        }

        updateWBalance(powerWatts)
        lastMovingSec = maxOf(lastMovingSec, movingSec)
    }

    fun npWatts(): Int {
        if (count4thPowers == 0L) return 0
        return (sumOf4thPowers / count4thPowers).pow(0.25).roundToInt()
    }

    fun ifValue(): Float {
        val ftp = ftpWatts.toFloat()
        return if (ftp > 0f) (npWatts() / ftp).coerceAtMost(2.0f) else 0f
    }

    fun viValue(): Float {
        if (totalPowerCount == 0L) return 0f
        val avg = totalPowerSum.toFloat() / totalPowerCount
        return if (avg > 0f) (npWatts() / avg).coerceAtMost(2.0f) else 0f
    }

    fun tssValue(movingSec: Long): Float {
        val ftp = ftpWatts.toFloat()
        if (ftp <= 0f || movingSec <= 0L) return 0f
        val np = safetyFloat(npWatts().toFloat())
        if (np <= 0f) return 0f
        val ifVal = (np / ftp).coerceIn(0f, 2f)
        val result = ((movingSec * np * ifVal) / (ftp * 3600f) * 100f)
        return result.coerceIn(0f, 9999f)
    }

    fun caloriesKcal(): Int = totalEnergyKj.roundToInt()

    fun hasDecouplingData(): Boolean =
        decoupleHr.size >= 120

    fun decouplingPercent(): Float {
        val n = decoupleHr.size
        if (n < 120) return 0f

        val half = n / 2
        val firstHr = decoupleHr.subList(0, half).average()
        val firstPwr = decouplePower.subList(0, half).average()
        val secondHr = decoupleHr.subList(half, n).average()
        val secondPwr = decouplePower.subList(half, n).average()

        if (firstPwr <= 0.0 || secondPwr <= 0.0) return 0f

        val r1 = firstHr / firstPwr
        val r2 = secondHr / secondPwr
        if (r1 <= 0.0) return 0f

        val drift = ((r2 - r1) / r1) * 100f
        return drift.toFloat().coerceIn(0f, 50f)
    }

    fun carbsGPerH(intensityFactor: Float, movingSec: Long, vi: Float, tempCelsius: Float?, bodyWeightKg: Float): Int {
        val ifClamped = intensityFactor.coerceIn(0.4f, 1.1f)
        var carbs = 25f + ((ifClamped - 0.4f) / 0.7f) * 65f
        val movingHours = movingSec / 3600f
        val durationMultiplier = when {
            movingHours < 1.0f -> 1.0f
            movingHours < 2.0f -> 1.08f
            movingHours < 3.0f -> 1.15f
            else -> 1.22f
        }
        val viMultiplier = when {
            vi <= 1.05f -> 1.0f
            vi <= 1.12f -> 1.05f
            else -> 1.10f
        }
        val weightMultiplier = (bodyWeightKg / 75f).coerceIn(0.85f, 1.20f)
        val tempMultiplier = when {
            tempCelsius == null -> 1.0f
            tempCelsius < 5f -> 0.95f
            tempCelsius < 25f -> 1.0f
            tempCelsius < 32f -> 1.05f
            else -> 1.08f
        }
        val result = carbs * durationMultiplier * viMultiplier * weightMultiplier * tempMultiplier
        return roundToNearest5(result).coerceIn(20, 110)
    }

    fun fluidLPerH(intensityFactor: Float, tempCelsius: Float?): Float {
        val z = intensityFactor
        val base = when { z < 0.55f -> 0.40f; z < 0.75f -> 0.50f; z < 0.87f -> 0.60f; else -> 0.70f }
        val tm = when {
            tempCelsius == null -> 1.0f
            tempCelsius < 5f -> 0.75f
            tempCelsius < 12f -> 0.85f
            tempCelsius < 18f -> 0.95f
            tempCelsius < 24f -> 1.10f
            tempCelsius < 30f -> 1.30f
            tempCelsius < 35f -> 1.50f
            else -> 1.70f
        }
        val hm = when { humidityPercent < 40f -> 0.90f; humidityPercent < 60f -> 1.00f; humidityPercent < 75f -> 1.10f; humidityPercent < 85f -> 1.20f; else -> 1.30f }
        return ((base * tm * hm * (bodyWeightKg / 70f) / 0.05f).roundToInt() * 0.05f).coerceIn(0.30f, 1.50f)
    }

    fun rideReservePercent(tss: Float, intensityFactor: Float, decoupling: Float): Int {
        val tssSafe = safetyFloat(tss)
        val ifSafe = safetyFloat(intensityFactor)
        val decoupleSafe = safetyFloat(decoupling)

        // Base reserve from todayFactor - used as maximum cap
        val baseReserve = safetyFloat(todayFactor) * 100f

        var reserve = baseReserve
        if (tssSafe > 0f) {
            reserve -= tssSafe * 0.6f
        }
        if (ifSafe > 0.75f) reserve -= (ifSafe - 0.75f) * 80f
        if (hasDecouplingData() && decoupleSafe > 5f) reserve -= (decoupleSafe - 5f) * 2f

        // Raw reserve clamped to startReserve (never exceed initial reserve)
        val raw = reserve.coerceIn(0f, startReserve)

        // Asymmetric smoothing: instant drop, slow recovery (2% per tick)
        // But never exceed startReserve even with smoothing
        lastReserve = if (raw < lastReserve) {
            raw
        } else {
            val recovery = lastReserve + (raw - lastReserve) * 0.02f
            recovery.coerceAtMost(startReserve)
        }

        return lastReserve.roundToInt().coerceIn(0, 100)
    }

    fun resetReserveGuard() { lastReserve = 100f }

    fun reset() {
        powerBuffer30s.clear(); sumOf4thPowers = 0.0; count4thPowers = 0L
        totalPowerSum = 0L; totalPowerCount = 0L; totalEnergyKj = 0.0
        decoupleHr.clear(); decouplePower.clear()
        wBalKj = wPrimeKj
        lastMovingSec = 0L
        lastReserve = 100f
        startReserve = 100f
    }

    // ── HRD: Local HR Drift/Strain ─────────────────────────────────────
    data class HrdResult(
        val phase: String,        // WAIT, BASE, ACTIVE, COOLDOWN
        val status: String,      // WAIT, BASE, INVALID, OK, +, ++, HOT
        val pct: Float,          // drift percentage (0 if status != OK/+/++/HOT)
        val valid: Boolean,      // true if we have enough data to show result
        val reason: String,     // diagnostic reason
        // diagnostics
        val baselineRatio: Float? = null,
        val currentRatio: Float? = null,
        val baselineSamples: Int = 0,
        val currentSamples: Int = 0,
        val totalSamples: Int = 0,
        val minPower: Int = 0,
    )

    fun updateHRD(
        nowMs: Long,
        elapsedSec: Long,
        movingSec: Long,
        powerWatts: Int,
        hrBpm: Int,
        cadenceRpm: Int,
    ): HrdResult {
        val MIN_POWER_RATIO = 0.55f
        val MIN_BASELINE_SAMPLES = 8
        val MIN_CURRENT_SAMPLES = 5
        val minPower = (ftpWatts * MIN_POWER_RATIO).toInt()

        // Check for long stop - if stopped > 10 min
        val stoppedDurationSec = elapsedSec - movingSec
        if (stoppedDurationSec > 600 && hrdStopStartMs == 0L) {
            // Stop detected - start tracking cooldown period
            hrdStopStartMs = nowMs
        }

        // COOLDOWN: after stop, require 10 min moving before HRD becomes valid
        if (hrdStopStartMs > 0) {
            val cooldownEndMs = hrdStopStartMs + 600_000L  // 10 min after stop ended
            if (nowMs < cooldownEndMs) {
                // Clear old samples that are before the cooldown period
                hrdSamples.removeAll { it.timestampMs < hrdStopStartMs }
                hrdBaselineRatio = null
                // Still in cooldown
                return HrdResult(
                    phase = "COOLDOWN",
                    status = "WAIT",
                    pct = 0f,
                    valid = false,
                    reason = "post_stop_cooldown",
                    totalSamples = hrdSamples.size,
                    minPower = minPower,
                )
            } else {
                // Cooldown over - reset baseline
                hrdSamples.clear()
                hrdBaselineRatio = null
                hrdStopStartMs = 0L
            }
        }

        // Valid sample criteria:
        // - movingSec > 0
        // - power > 55% FTP
        // - HR > 0
        // - cadence > 0
        val isMoving = movingSec > 0
        val isValidSample = isMoving && powerWatts > minPower && hrBpm > 0 && cadenceRpm > 0

        if (isValidSample) {
            hrdSamples.add(HrdSample(nowMs, hrBpm, powerWatts, cadenceRpm))
        }

        // Clean old samples - keep last 60 min max
        val cutoffMs = nowMs - 3_600_000L
        hrdSamples.removeAll { it.timestampMs < cutoffMs }

        // Determine phase based on moving time
        val movingMin = movingSec / 60

        // Phase: WAIT (0-20 min)
        if (movingMin < 20) {
            return HrdResult(
                phase = "WAIT",
                status = "WAIT",
                pct = 0f,
                valid = false,
                reason = "moving_under_20min",
                totalSamples = hrdSamples.size,
                minPower = minPower,
            )
        }

        // Build baseline from samples 20-35 min ago (rolling window)
        val baselineWindowStartMs = nowMs - 2_100_000L  // 35 min ago
        val baselineWindowEndMs = nowMs - 1_200_000L   // 20 min ago
        val baselineSamples = hrdSamples.filter { it.timestampMs in baselineWindowStartMs..baselineWindowEndMs }

        // Phase: BASE (20-35 min)
        if (movingMin < 35) {
            if (baselineSamples.size < MIN_BASELINE_SAMPLES) {
                return HrdResult(
                    phase = "BASE",
                    status = "BASE",
                    pct = 0f,
                    valid = false,
                    reason = "building_baseline",
                    baselineSamples = baselineSamples.size,
                    totalSamples = hrdSamples.size,
                    minPower = minPower,
                )
            }
            // Baseline ready but not enough time yet
            val baselineRatio = baselineSamples.map { it.hr.toFloat() / it.power }.average().toFloat()
            hrdBaselineRatio = baselineRatio
            return HrdResult(
                phase = "BASE",
                status = "BASE",
                pct = 0f,
                valid = false,
                reason = "baseline_ready",
                baselineRatio = baselineRatio,
                baselineSamples = baselineSamples.size,
                totalSamples = hrdSamples.size,
                minPower = minPower,
            )
        }

        // Phase: ACTIVE (35+ min) - calculate drift
        if (baselineSamples.size < MIN_BASELINE_SAMPLES) {
            return HrdResult(
                phase = "ACTIVE",
                status = "INVALID",
                pct = 0f,
                valid = false,
                reason = "insufficient_baseline_samples",
                baselineSamples = baselineSamples.size,
                totalSamples = hrdSamples.size,
                minPower = minPower,
            )
        }

        // Current window: last 15 min of valid samples
        val currentWindowStartMs = nowMs - 900_000L  // 15 min ago
        val currentSamples = hrdSamples.filter { it.timestampMs >= currentWindowStartMs }

        if (currentSamples.size < MIN_CURRENT_SAMPLES) {
            return HrdResult(
                phase = "ACTIVE",
                status = "INVALID",
                pct = 0f,
                valid = false,
                reason = "insufficient_current_samples",
                baselineRatio = hrdBaselineRatio,
                baselineSamples = baselineSamples.size,
                currentSamples = currentSamples.size,
                totalSamples = hrdSamples.size,
                minPower = minPower,
            )
        }

        // Calculate ratios
        val baselineRatio = baselineSamples.map { it.hr.toFloat() / it.power }.average().toFloat()
        val currentRatio = currentSamples.map { it.hr.toFloat() / it.power }.average().toFloat()

        // Store baseline (not updating continuously - fixed baseline from 20-35 min window)
        if (hrdBaselineRatio == null) {
            hrdBaselineRatio = baselineRatio
        }

        // Calculate drift
        val driftPct = ((currentRatio / baselineRatio) - 1.0f) * 100f
        val displayDrift = maxOf(0f, driftPct)

        val status = when {
            displayDrift < 3f -> "OK"
            displayDrift < 5f -> "+"
            displayDrift < 7f -> "++"
            else -> "HOT"
        }

        return HrdResult(
            phase = "ACTIVE",
            status = status,
            pct = displayDrift,
            valid = true,
            reason = "drift_calculated",
            baselineRatio = baselineRatio,
            currentRatio = currentRatio,
            baselineSamples = baselineSamples.size,
            currentSamples = currentSamples.size,
            totalSamples = hrdSamples.size,
            minPower = minPower,
)
    }

    fun resetHRD() {
        hrdSamples.clear()
        hrdBaselineRatio = null
        hrdStopStartMs = 0L
    }

    private fun Double.roundToInt(): Int = Math.round(this).toInt()
    private fun Float.roundToInt(): Int = Math.round(this)
    private fun roundToNearest5(value: Float): Int = (Math.round(value / 5f) * 5)

    companion object {
        @JvmStatic fun safetyFloat(v: Float): Float =
            if (v.isNaN() || v.isInfinite()) 0f else v.coerceAtLeast(0f)

        @JvmStatic fun safetyDouble(v: Double): Double =
            if (v.isNaN() || v.isInfinite()) 0.0 else v.coerceAtLeast(0.0)

        @JvmStatic fun safetyInt(v: Int): Int = maxOf(v, 0)
    }
}