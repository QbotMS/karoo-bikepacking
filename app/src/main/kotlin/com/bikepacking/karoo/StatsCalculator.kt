package com.bikepacking.karoo

import kotlin.math.*

class StatsCalculator(private val settings: AppSettings) {

    // ── NP buffer ────────────────────────────────────────────────────────
    private val powerBuffer30s = ArrayDeque<Int>(30)
    private var sumOf4thPowers = 0.0
    private var count4thPowers = 0L

    // ── Avg Power ────────────────────────────────────────────────────────
    private var totalPowerSum = 0L
    private var totalPowerCount = 0L
    private var totalEnergyKj = 0.0

    // ── Decoupling ───────────────────────────────────────────────────────
    private var firstHalfHrSum = 0.0; private var firstHalfPowerSum = 0.0; private var firstHalfCount = 0L
    private var secondHalfHrSum = 0.0; private var secondHalfPowerSum = 0.0; private var secondHalfCount = 0L

    // ── Readiness ─────────────────────────────────────────────────────────
    var todayFactor: Float = 1.0f
    var ctl: Float = 60f
    var bodyWeightKg: Float = 75f
    var humidityPercent: Float = 50f

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
        powerBuffer30s.addLast(powerWatts)
        if (powerBuffer30s.size > 30) powerBuffer30s.removeFirst()
        if (powerBuffer30s.size == 30) {
            val avg30s = powerBuffer30s.average()
            sumOf4thPowers += avg30s.pow(4)
            count4thPowers++
        }
        totalPowerSum += powerWatts; totalPowerCount++
        totalEnergyKj += powerWatts / 1000.0
        if (heartRate > 0 && powerWatts > 0 && movingSec > 0) {
            if (firstHalfCount < (elapsedSec / 2).coerceAtLeast(30)) {
                firstHalfHrSum += heartRate; firstHalfPowerSum += powerWatts; firstHalfCount++
            } else {
                secondHalfHrSum += heartRate; secondHalfPowerSum += powerWatts; secondHalfCount++
            }
        }
        updateWBalance(powerWatts)
    }

    fun npWatts(): Int {
        if (count4thPowers == 0L) return 0
        return (sumOf4thPowers / count4thPowers).pow(0.25).roundToInt()
    }

    fun ifValue(): Float {
        val ftp = settings.ftp.toFloat()
        return if (ftp > 0f) (npWatts() / ftp).coerceAtMost(2.0f) else 0f
    }

    fun viValue(): Float {
        if (totalPowerCount == 0L) return 0f
        val avg = totalPowerSum.toFloat() / totalPowerCount
        return if (avg > 0f) (npWatts() / avg).coerceAtMost(2.0f) else 0f
    }

    fun tssValue(movingSec: Long): Float {
        val ftp = settings.ftp.toFloat()
        if (ftp <= 0f || movingSec <= 0L) return 0f
        val np = npWatts().toFloat(); val ifVal = np / ftp
        return ((movingSec * np * ifVal) / (ftp * 3600f) * 100f).coerceAtMost(9999f)
    }

    fun caloriesKcal(): Int = totalEnergyKj.roundToInt()

    fun decouplingPercent(): Float {
        if (firstHalfCount < 30 || secondHalfCount < 30) return 0f
        val r1 = (firstHalfHrSum / firstHalfCount) / (firstHalfPowerSum / firstHalfCount)
        val r2 = (secondHalfHrSum / secondHalfCount) / (secondHalfPowerSum / secondHalfCount)
        if (r1 <= 0 || r2 <= 0) return 0f
        return (((r2 - r1) / r1) * 100f).toFloat().coerceIn(-20f, 50f)
    }

    fun carbsGPerH(if30: Float, elapsedSec: Long, vi: Float, tempCelsius: Float): Int {
        val ftp = settings.ftp.toFloat()
        val z = if (ftp > 0) if30 / ftp else 0f
        val base = when { z < 0.55f -> 30f; z < 0.75f -> 50f; z < 0.87f -> 65f; z < 1.00f -> 80f; else -> 90f }
        val dm = when { elapsedSec < 5400L -> 1.00f; elapsedSec < 7200L -> 1.10f; elapsedSec < 10800L -> 1.20f; else -> 1.30f }
        val vm = when { vi < 1.05f -> 1.00f; vi < 1.10f -> 1.05f; else -> 1.10f }
        val tm = when { tempCelsius < 25f -> 1.00f; tempCelsius < 30f -> 0.95f; else -> 0.90f }
        return (((base * dm * vm * tm / 5f).roundToInt() * 5).coerceIn(20, 100))
    }

    fun fluidLPerH(if30: Float, tempCelsius: Float): Float {
        val ftp = settings.ftp.toFloat()
        val z = if (ftp > 0) if30 / ftp else 0f
        val base = when { z < 0.55f -> 0.40f; z < 0.75f -> 0.50f; z < 0.87f -> 0.60f; else -> 0.70f }
        val tm = when { tempCelsius < 15f -> 0.80f; tempCelsius < 20f -> 1.00f; tempCelsius < 25f -> 1.15f; tempCelsius < 30f -> 1.30f; tempCelsius < 35f -> 1.50f; else -> 1.70f }
        val hm = when { humidityPercent < 40f -> 0.90f; humidityPercent < 60f -> 1.00f; humidityPercent < 75f -> 1.10f; humidityPercent < 85f -> 1.20f; else -> 1.30f }
        return ((base * tm * hm * (bodyWeightKg / 70f) / 0.05f).roundToInt() * 0.05f).coerceIn(0.30f, 1.20f)
    }

    fun rideReservePercent(tss: Float, if30: Float, remainingKm: Float, avgNetKph: Float, hasRoute: Boolean, decoupling: Float): Int {
        if (tss <= 0f) return (todayFactor * 100f).roundToInt().coerceIn(0, 100)
        val proj = if (hasRoute && avgNetKph > 1f && remainingKm > 0f) (remainingKm / avgNetKph) * if30 * if30 * 100f else 0f
        val total = tss + proj
        if (total <= 0f) return (todayFactor * 100f).roundToInt().coerceIn(0, 100)
        val depletion = (tss / total).coerceIn(0f, 1f)
        val pen = when { decoupling < 5f -> 1.00f; decoupling < 10f -> 0.95f; decoupling < 15f -> 0.90f; else -> 0.85f }
        return (todayFactor * (1f - depletion) * pen * 100f).roundToInt().coerceIn(-20, 100)
    }

    fun reset() {
        powerBuffer30s.clear(); sumOf4thPowers = 0.0; count4thPowers = 0L
        totalPowerSum = 0L; totalPowerCount = 0L; totalEnergyKj = 0.0
        firstHalfHrSum = 0.0; firstHalfPowerSum = 0.0; firstHalfCount = 0L
        secondHalfHrSum = 0.0; secondHalfPowerSum = 0.0; secondHalfCount = 0L
        wBalKj = wPrimeKj
    }

    private fun Double.roundToInt(): Int = Math.round(this).toInt()
    private fun Float.roundToInt(): Int = Math.round(this)
}