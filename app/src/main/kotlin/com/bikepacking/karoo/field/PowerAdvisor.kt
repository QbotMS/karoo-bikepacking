package com.bikepacking.karoo.field

data class PowerAdvisorResult(
    val state: FieldState,
    val targetLowWatts: Int,
    val targetHighWatts: Int,
    val reasonCode: String,
)

object PowerAdvisor {

    private const val SHORT_CLIMB_GRADE = 3f
    private const val SHORT_CLIMB_ASCENT_MAX = 500
    private const val LONG_RIDE_SEC = 14_400L

    fun assess(ctx: RideContext): PowerAdvisorResult {
        val watts = ctx.rider.smoothedPowerWatts
        val ftp = ctx.ftp

        if (ftp <= 0 || watts <= 0) {
            return PowerAdvisorResult(FieldState.NEUTRAL, 0, 0, "no_data")
        }

        val wBal = ctx.effort.wBalancePercent
        if (wBal >= 0 && wBal < 15) {
            return PowerAdvisorResult(FieldState.BAD, 0, 0, "wbal_critical")
        }

        val todayProfile = TodayProfile.from(ctx.todayFactor)
        val adjustedFtp = todayProfile.adjustedFtp(ftp)

        val (targetLow, targetHigh) = computeTargetRange(ctx, adjustedFtp)

        val baseState = compareToTarget(watts, targetLow, targetHigh)
        var state = baseState.first
        var reason = baseState.second

        val modifierCount = applyRiskModifiers(ctx, adjustedFtp)
        if (modifierCount > 0) {
            state = state.worsen(modifierCount.coerceAtMost(2))
            reason = "${reason}_mod${modifierCount}"
        }

        return PowerAdvisorResult(state, targetLow, targetHigh, reason)
    }

    private fun computeTargetRange(ctx: RideContext, adjustedFtp: Int): Pair<Int, Int> {
        val grade = ctx.rider.smoothedGradePercent
        val ascentLeft = ctx.route.ascentLeftM
        val elapsedSec = ctx.effort.elapsedSec
        val isLongRide = elapsedSec > LONG_RIDE_SEC

        val isShortClimb = grade > SHORT_CLIMB_GRADE && ascentLeft <= SHORT_CLIMB_ASCENT_MAX
        val isLongClimb = grade > SHORT_CLIMB_GRADE && ascentLeft > SHORT_CLIMB_ASCENT_MAX

        return when {
            isShortClimb -> {
                val high = (adjustedFtp * 1.05f).roundToInt()
                val low = (adjustedFtp * 0.80f).roundToInt()
                Pair(low, high)
            }
            isLongClimb -> {
                val high = (adjustedFtp * 0.75f).roundToInt()
                val low = (adjustedFtp * 0.55f).roundToInt()
                Pair(low, high)
            }
            isLongRide -> {
                val factor = 0.90f
                val high = (adjustedFtp * 0.87f * factor).roundToInt()
                val low = (adjustedFtp * 0.75f * factor).roundToInt()
                Pair(low, high)
            }
            else -> {
                val high = (adjustedFtp * 0.87f).roundToInt()
                val low = (adjustedFtp * 0.75f).roundToInt()
                Pair(low, high)
            }
        }
    }

    private fun compareToTarget(watts: Int, low: Int, high: Int): Pair<FieldState, String> {
        return when {
            watts < low -> {
                // Below target - neutral/muted, not alarm. 
                // Low power is normal (recovering, easy spin, downhill), not a problem.
                Pair(FieldState.NEUTRAL, "below_target")
            }
            watts > high -> {
                // Above target - the main concern, may indicate pushing too hard
                if (watts > (high * 1.20f).roundToInt()) {
                    Pair(FieldState.BAD, "far_above")
                } else {
                    Pair(FieldState.WARN, "above_target")
                }
            }
            else -> Pair(FieldState.GOOD, "in_range")
        }
    }

    private fun applyRiskModifiers(ctx: RideContext, adjustedFtp: Int): Int {
        var count = 0

        val hrDrift = ctx.effort.decouplingPercent
        if (hrDrift > 5f) count++

        val temp = ctx.effort.temperatureCelsius
        if (temp != null && temp > 35f) count++

        if (ctx.todayFactor < 0.85f) count++

        return count.coerceAtMost(3)
    }
}

private fun FieldState.worsen(steps: Int): FieldState {
    var s = this
    repeat(steps) {
        s = when (s) {
            FieldState.GOOD -> FieldState.OK
            FieldState.OK -> FieldState.WARN
            FieldState.WARN -> FieldState.BAD
            FieldState.BAD -> FieldState.BAD
            FieldState.NEUTRAL -> FieldState.WARN  // NEUTRAL can be worsened to WARN
        }
    }
    return s
}
