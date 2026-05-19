package com.bikepacking.karoo.message

import com.bikepacking.karoo.field.ClimbInfo
import com.bikepacking.karoo.field.TodayProfile

data class ClimbPowerTarget(
    val targetLowW: Int,
    val targetMidW: Int,
    val targetHighW: Int,
    val reasonCode: String,
)

object ClimbPowerTargetCalculator {

    fun calculate(
        activeClimb: ClimbInfo?,
        ftp: Int,
        todayFactor: Float,
        wBalancePercent: Int? = null,
        hrDrift: Float? = null,
        routeAscentRemainingM: Double? = null,
        routeDistanceRemainingKm: Float? = null,
    ): ClimbPowerTarget? {
        if (ftp <= 0 || activeClimb == null) return null

        val adjustedFtp = TodayProfile.from(todayFactor).adjustedFtp(ftp)
        val reasons = mutableListOf<String>()

        var factor = 1.0f

        val lengthKm = activeClimb.lengthM / 1000.0
        val grade = activeClimb.averageGradePercent

        when {
            lengthKm >= 5.0 && grade > 4.0 -> {
                factor *= 0.80f
                reasons.add("long")
            }
            lengthKm < 2.0 && grade > 6.0 -> {
                factor *= 0.95f
                reasons.add("short")
            }
            grade > 4.0 -> {
                factor *= 0.85f
                reasons.add("longish")
            }
            else -> {
                factor *= 0.85f
                reasons.add("moderate")
            }
        }

        val ascentLeft = routeAscentRemainingM ?: 0.0
        when {
            ascentLeft > 2000 -> { factor *= 0.75f; reasons.add("up+2k") }
            ascentLeft > 1000 -> { factor *= 0.80f; reasons.add("up+1k") }
            ascentLeft > 500 -> { factor *= 0.90f; reasons.add("up+500") }
        }

        val distLeft = routeDistanceRemainingKm ?: 0f
        when {
            distLeft > 80f -> { factor *= 0.85f; reasons.add("80+km") }
            distLeft > 50f -> { factor *= 0.90f; reasons.add("50+km") }
            distLeft > 20f -> { factor *= 0.95f; reasons.add("20+km") }
        }

        when {
            wBalancePercent != null && wBalancePercent < 15 -> { factor *= 0.80f; reasons.add("wbal0") }
            wBalancePercent != null && wBalancePercent < 30 -> { factor *= 0.85f; reasons.add("wbal30") }
            wBalancePercent != null && wBalancePercent < 50 -> { factor *= 0.92f; reasons.add("wbal50") }
        }

        when {
            hrDrift != null && hrDrift > 10f -> { factor *= 0.80f; reasons.add("drift10") }
            hrDrift != null && hrDrift > 5f -> { factor *= 0.90f; reasons.add("drift5") }
        }

        val enduranceMin = (adjustedFtp * 0.55f).roundToInt()
        val tempoMax = (adjustedFtp * 1.05f).roundToInt()

        val mid = (adjustedFtp * factor).roundToInt().coerceIn(enduranceMin, tempoMax)
        val low = (mid * 0.90f).roundToInt().coerceAtLeast(enduranceMin)
        val high = (mid * 1.10f).roundToInt().coerceAtMost(tempoMax)

        val reason = reasons.joinToString("_").ifEmpty { "base" }

        return ClimbPowerTarget(low, mid, high, reason)
    }

    private fun Float.roundToInt(): Int = (this + 0.5f).toInt()
}
