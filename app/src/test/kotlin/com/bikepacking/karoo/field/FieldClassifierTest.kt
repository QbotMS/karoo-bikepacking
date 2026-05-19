package com.bikepacking.karoo.field

import org.junit.Assert.assertEquals
import org.junit.Test

class FieldClassifierTest {

    private fun ctx(
        speed: Float = 0f,
        power: Int = 0,
        hr: Int = 0,
        cad: Int = 0,
        grade: Float = 0f,
        wBal: Int = -1,
        movingSec: Long = 0L,
        distanceKm: Float = 0f,
        ftp: Int = 200,
        maxHr: Int = 180,
        front: Int = 0,
        rear: Int = 0,
    ): RideContext = RideContext(
        rider = RiderState(
            speedKph = speed,
            powerWatts = power,
            heartRate = hr,
            cadenceRpm = cad,
            gradePercent = grade,
            frontTeeth = front,
            rearTeeth = rear,
            distanceKm = distanceKm,
            smoothedPowerWatts = power,
            smoothedHeartRate = hr,
            smoothedCadenceRpm = cad,
            smoothedGradePercent = grade,
        ),
        route = RouteContext(remainingKm = 0f, ascentLeftM = 0, timeToFinishSec = 0L, hasRoute = false),
        effort = EffortContext(wBalancePercent = wBal, decouplingPercent = 0f, elapsedSec = 0L, movingSec = movingSec, temperatureCelsius = null),
        gearCtx = GearContext(frontTeeth = front, rearTeeth = rear),
        todayFactor = 1.0f,
        ftp = ftp,
        maxHr = maxHr,
    )

    // ── SPEED ──

    @Test
    fun `speed returns NEUTRAL when net avg below 1`() {
        assertEquals(FieldState.NEUTRAL, FieldClassifier.speed(ctx(speed = 5f, movingSec = 60L, distanceKm = 0.01f)))
    }

    @Test
    fun `speed returns GOOD when above 115 percent of net avg`() {
        assertEquals(FieldState.GOOD, FieldClassifier.speed(ctx(speed = 30f, movingSec = 3600L, distanceKm = 20f)))
    }

    @Test
    fun `speed returns BAD when below 85 percent of net avg`() {
        assertEquals(FieldState.BAD, FieldClassifier.speed(ctx(speed = 10f, movingSec = 3600L, distanceKm = 20f)))
    }

    @Test
    fun `speed returns OK when within 15 percent of net avg`() {
        assertEquals(FieldState.OK, FieldClassifier.speed(ctx(speed = 20f, movingSec = 3600L, distanceKm = 20f)))
    }

    // ── POWER (delegates to PowerAdvisor) ──

    @Test
    fun `power returns NEUTRAL when ftp is zero`() {
        assertEquals(FieldState.NEUTRAL, FieldClassifier.power(ctx(power = 150, ftp = 0)))
    }

    @Test
    fun `power returns NEUTRAL when watts are zero`() {
        assertEquals(FieldState.NEUTRAL, FieldClassifier.power(ctx(power = 0, ftp = 200)))
    }

    @Test
    fun `power returns GOOD when in sustainable range`() {
        assertEquals(FieldState.GOOD, FieldClassifier.power(ctx(power = 160, ftp = 200)))
    }

    @Test
    fun `power returns WARN when slightly above sustainable range`() {
        assertEquals(FieldState.WARN, FieldClassifier.power(ctx(power = 190, ftp = 200)))
    }

    @Test
    fun `power returns BAD when far above sustainable range`() {
        assertEquals(FieldState.BAD, FieldClassifier.power(ctx(power = 240, ftp = 200)))
    }

    @Test
    fun `power returns BAD when wBal 0-14`() {
        assertEquals(FieldState.BAD, FieldClassifier.power(ctx(power = 150, ftp = 200, wBal = 10)))
    }

    @Test
    fun `power returns GOOD when in range with wBal 20`() {
        // WBal 15-29 no longer forces WARN - removed that logic
        assertEquals(FieldState.GOOD, FieldClassifier.power(ctx(power = 150, ftp = 200, wBal = 20)))
    }

    // ── HR ──

    @Test
    fun `hr returns NEUTRAL when maxHr is zero`() {
        assertEquals(FieldState.NEUTRAL, FieldClassifier.hr(100, 0, -1))
    }

    @Test
    fun `hr returns NEUTRAL when bpm is zero`() {
        assertEquals(FieldState.NEUTRAL, FieldClassifier.hr(0, 180, -1))
    }

    @Test
    fun `hr returns GOOD for Z2`() {
        assertEquals(FieldState.GOOD, FieldClassifier.hr(130, 180, -1))
    }

    @Test
    fun `hr returns OK for Z1`() {
        assertEquals(FieldState.OK, FieldClassifier.hr(95, 180, -1))
    }

    @Test
    fun `hr returns OK for Z3`() {
        assertEquals(FieldState.OK, FieldClassifier.hr(150, 180, -1))
    }

    @Test
    fun `hr returns WARN for Z4`() {
        assertEquals(FieldState.WARN, FieldClassifier.hr(165, 180, -1))
    }

    @Test
    fun `hr returns BAD for Z5`() {
        assertEquals(FieldState.BAD, FieldClassifier.hr(175, 180, -1))
    }

    // ── CADENCE ──

    @Test
    fun `cadence returns NEUTRAL when rpm is zero`() {
        assertEquals(FieldState.NEUTRAL, FieldClassifier.cadence(0))
    }

    @Test
    fun `cadence returns GOOD when in flat target 60-70`() {
        assertEquals(FieldState.GOOD, FieldClassifier.cadence(65))
    }

    @Test
    fun `cadence returns GOOD when in climb target 55-65`() {
        assertEquals(FieldState.GOOD, FieldClassifier.cadence(60, 6f))
    }

    @Test
    fun `cadence returns GOOD when in descent target 65-75`() {
        assertEquals(FieldState.GOOD, FieldClassifier.cadence(70, -6f))
    }

    @Test
    fun `cadence returns OK for near-target flat`() {
        assertEquals(FieldState.OK, FieldClassifier.cadence(57))
    }

    @Test
    fun `cadence returns BAD when well above target`() {
        assertEquals(FieldState.BAD, FieldClassifier.cadence(90))
    }

    @Test
    fun `cadence returns BAD when well below target`() {
        assertEquals(FieldState.BAD, FieldClassifier.cadence(40))
    }

    // ── GRADE ──

    @Test
    fun `grade returns GOOD for flat -2 to +2`() {
        assertEquals(FieldState.GOOD, FieldClassifier.grade(0f))
    }

    @Test
    fun `grade returns OK for moderate 2 to 5`() {
        assertEquals(FieldState.OK, FieldClassifier.grade(3f))
    }

    @Test
    fun `grade returns OK for moderate -5 to -2`() {
        assertEquals(FieldState.OK, FieldClassifier.grade(-3f))
    }

    @Test
    fun `grade returns WARN for steep 5 to 9`() {
        assertEquals(FieldState.WARN, FieldClassifier.grade(7f))
    }

    @Test
    fun `grade returns WARN for steep descent below -5`() {
        assertEquals(FieldState.WARN, FieldClassifier.grade(-6f))
    }

    @Test
    fun `grade returns BAD for very steep above 9`() {
        assertEquals(FieldState.BAD, FieldClassifier.grade(10f))
    }

    // ── Grade boundary tests ──

    @Test
    fun `grade returns GOOD at exactly 2`() {
        assertEquals(FieldState.GOOD, FieldClassifier.grade(2.0f))
    }

    @Test
    fun `grade returns OK at just above 2`() {
        assertEquals(FieldState.OK, FieldClassifier.grade(2.1f))
    }

    @Test
    fun `grade returns OK at exactly 4 point 5`() {
        assertEquals(FieldState.OK, FieldClassifier.grade(4.5f))
    }

    // ── GEAR ──

    private fun gearCtx(
        cad: Int = 65, power: Int = 150, grade: Float = 1f, ftp: Int = 200,
    ) = ctx(cad = cad, power = power, grade = grade, ftp = ftp)

    @Test
    fun `gear returns NEUTRAL when no power`() {
        assertEquals(FieldState.NEUTRAL, FieldClassifier.gear(60, 0, 0f, 200, -1))
    }

    @Test
    fun `gear returns GOOD for sweet spot`() {
        assertEquals(FieldState.GOOD, FieldClassifier.gear(65, 150, 1f, 200, -1))
    }

    @Test
    fun `gear returns BAD when grinding uphill`() {
        assertEquals(FieldState.BAD, FieldClassifier.gear(45, 250, 6f, 200, -1))
    }

    @Test
    fun `gear returns WARN when spinning too easy`() {
        assertEquals(FieldState.WARN, FieldClassifier.gear(95, 60, 0f, 200, -1))
    }

    @Test
    fun `gear returns OK for moderate conditions`() {
        assertEquals(FieldState.OK, FieldClassifier.gear(65, 200, 3f, 200, -1))
    }
}
