package com.bikepacking.karoo.field

data class GearAdvisorResult(
    val state: FieldState,
    val reasonCode: String,
)

object GearAdvisor {

    fun assess(ctx: RideContext): GearAdvisorResult {
        val cad = ctx.rider.smoothedCadenceRpm
        val power = ctx.rider.smoothedPowerWatts
        val grade = ctx.rider.smoothedGradePercent
        val ftp = ctx.ftp
        val front = ctx.rider.frontTeeth
        val rear = ctx.rider.rearTeeth

        if (cad <= 0 || power <= 0 || (front <= 0 && rear <= 0)) {
            return GearAdvisorResult(FieldState.NEUTRAL, "no_data")
        }

        val todayProfile = TodayProfile.from(ctx.todayFactor)
        val adjustedFtp = todayProfile.adjustedFtp(ftp)

        if (adjustedFtp <= 0) {
            return GearAdvisorResult(FieldState.NEUTRAL, "no_ftp")
        }

        // Grinding uphill — very high power, steep grade, very low cadence
        if (cad <= 50 && power >= (adjustedFtp * 1.10f).toInt() && grade >= 5f) {
            return GearAdvisorResult(FieldState.BAD, "too_hard")
        }

        // Low cadence warning — moderately high power on a rise
        if (cad < 55 && power >= (adjustedFtp * 0.75f).toInt() && grade >= 2f) {
            return GearAdvisorResult(FieldState.WARN, "likely_too_hard")
        }

        // Spinning too light — very high cadence, very low power
        if (cad >= 90 && power <= (adjustedFtp * 0.50f).toInt()) {
            return GearAdvisorResult(FieldState.WARN, "too_easy")
        }

        // Sweet spot — cadence 60–75, power in sustainable range, grade not extreme
        val inCadenceRange = cad in 60..75
        val sustainableLow = (adjustedFtp * 0.75f).toInt()
        val sustainableHigh = (adjustedFtp * 0.87f).toInt()
        val powerOk = power in sustainableLow..sustainableHigh
        val gradeOk = grade > -5f && grade < 5f

        if (inCadenceRange && powerOk && gradeOk) {
            return GearAdvisorResult(FieldState.GOOD, "sweet_spot")
        }

        return GearAdvisorResult(FieldState.OK, "acceptable")
    }
}
