package com.bikepacking.karoo.field

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class GearAdvisorTest {

    @Before
    fun setUp() {
        FieldClassifier.resetHysteresis()
    }

    private fun ctx(
        cad: Int = 65,
        power: Int = 150,
        grade: Float = 1f,
        ftp: Int = 200,
        front: Int = 40,
        rear: Int = 15,
    ): RideContext = RideContext(
        rider = RiderState(
            speedKph = 20f, powerWatts = power, heartRate = 130,
            cadenceRpm = cad, gradePercent = grade, frontTeeth = front, rearTeeth = rear,
            distanceKm = 10f,
            smoothedPowerWatts = power,
            smoothedHeartRate = 130,
            smoothedCadenceRpm = cad,
            smoothedGradePercent = grade,
        ),
        route = RouteContext(remainingKm = 50f, ascentLeftM = 0, timeToFinishSec = 0L, hasRoute = false),
        effort = EffortContext(wBalancePercent = -1, decouplingPercent = 0f, elapsedSec = 0L, movingSec = 0L, temperatureCelsius = null),
        gearCtx = GearContext(frontTeeth = front, rearTeeth = rear),
        todayFactor = 1.0f,
        ftp = ftp,
        maxHr = 180,
    )

    @Test
    fun `no cadence returns NEUTRAL`() {
        val r = GearAdvisor.assess(ctx(cad = 0))
        assertEquals(FieldState.NEUTRAL, r.state)
        assertEquals("no_data", r.reasonCode)
    }

    @Test
    fun `no gear data returns NEUTRAL`() {
        val r = GearAdvisor.assess(ctx(front = 0, rear = 0))
        assertEquals(FieldState.NEUTRAL, r.state)
        assertEquals("no_data", r.reasonCode)
    }

    @Test
    fun `grinding uphill returns BAD`() {
        val r = GearAdvisor.assess(ctx(cad = 45, power = 250, grade = 8f))
        assertEquals(FieldState.BAD, r.state)
        assertEquals("too_hard", r.reasonCode)
    }

    @Test
    fun `low cadence on rise returns WARN`() {
        val r = GearAdvisor.assess(ctx(cad = 52, power = 180, grade = 4f))
        assertEquals(FieldState.WARN, r.state)
        assertEquals("likely_too_hard", r.reasonCode)
    }

    @Test
    fun `spinning too light returns WARN`() {
        val r = GearAdvisor.assess(ctx(cad = 95, power = 60))
        assertEquals(FieldState.WARN, r.state)
        assertEquals("too_easy", r.reasonCode)
    }

    @Test
    fun `sweet spot returns GOOD`() {
        val r = GearAdvisor.assess(ctx(cad = 65, power = 160, grade = 1f))
        assertEquals(FieldState.GOOD, r.state)
        assertEquals("sweet_spot", r.reasonCode)
    }

    @Test
    fun `acceptable on steep grade not BAD`() {
        val r = GearAdvisor.assess(ctx(cad = 70, power = 160, grade = 6f))
        // cad in range, power in range, but grade 6% fails gradeOk -> falls to OK
        assertEquals(FieldState.OK, r.state)
        assertEquals("acceptable", r.reasonCode)
    }

    @Test
    fun `hysteresis prevents flicker through FieldClassifier`() {
        val ctxGood = ctx(cad = 65, power = 160, grade = 1f)
        val ctxBad = ctx(cad = 45, power = 250, grade = 8f)

        // first call initializes hysteresis to GOOD
        val first = FieldClassifier.gear(ctxGood, nowMs = 1000L)
        assertEquals(FieldState.GOOD, first)

        // immediate switch to BAD should be blocked by hysteresis (30s)
        val second = FieldClassifier.gear(ctxBad, nowMs = 2000L)
        assertEquals(FieldState.GOOD, second)

        // after 31s it should switch
        val third = FieldClassifier.gear(ctxBad, nowMs = 32000L)
        assertEquals(FieldState.BAD, third)

        FieldClassifier.resetHysteresis()
    }
}
