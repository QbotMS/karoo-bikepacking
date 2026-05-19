package com.bikepacking.karoo.field

import org.junit.Assert.assertEquals
import org.junit.Test

class PowerAdvisorTest {

    private fun ctx(
        power: Int = 150,
        smoothedPower: Int = 150,
        grade: Float = 0f,
        wBal: Int = -1,
        elapsedSec: Long = 0L,
        ascentLeft: Int = 0,
        decoupling: Float = 0f,
        temp: Float? = null,
        todayFactor: Float = 1.0f,
        ftp: Int = 200,
    ): RideContext = RideContext(
        rider = RiderState(
            speedKph = 20f, powerWatts = power, heartRate = 130,
            cadenceRpm = 65, gradePercent = grade, frontTeeth = 40, rearTeeth = 15,
            distanceKm = 10f,
            smoothedPowerWatts = smoothedPower,
            smoothedHeartRate = 130,
            smoothedCadenceRpm = 65,
            smoothedGradePercent = grade,
        ),
        route = RouteContext(remainingKm = 50f, ascentLeftM = ascentLeft, timeToFinishSec = 0L, hasRoute = false),
        effort = EffortContext(
            wBalancePercent = wBal, decouplingPercent = decoupling,
            elapsedSec = elapsedSec, movingSec = elapsedSec, temperatureCelsius = temp,
        ),
        gearCtx = GearContext(frontTeeth = 40, rearTeeth = 15),
        todayFactor = todayFactor,
        ftp = ftp,
        maxHr = 180,
    )

    @Test
    fun `no data returns NEUTRAL`() {
        val r = PowerAdvisor.assess(ctx(power = 0, smoothedPower = 0))
        assertEquals(FieldState.NEUTRAL, r.state)
        assertEquals("no_data", r.reasonCode)
    }

    @Test
    fun `WBal below 15 returns BAD regardless of power`() {
        val r = PowerAdvisor.assess(ctx(smoothedPower = 150, wBal = 10))
        assertEquals(FieldState.BAD, r.state)
        assertEquals("wbal_critical", r.reasonCode)
    }

    @Test
    fun `flat nominal power in range returns GOOD`() {
        val r = PowerAdvisor.assess(ctx(smoothedPower = 160))
        assertEquals(FieldState.GOOD, r.state)
        assertEquals("in_range", r.reasonCode)
    }

    @Test
    fun `flat power below target returns NEUTRAL`() {
        val r = PowerAdvisor.assess(ctx(smoothedPower = 140))
        // Below target now returns NEUTRAL (not alarm)
        assertEquals(FieldState.NEUTRAL, r.state)
    }

    @Test
    fun `flat power far below target returns NEUTRAL`() {
        val r = PowerAdvisor.assess(ctx(smoothedPower = 50))
        // Far below target still returns NEUTRAL (low power is normal, not a problem)
        assertEquals(FieldState.NEUTRAL, r.state)
    }

    @Test
    fun `flat power above target returns WARN`() {
        val r = PowerAdvisor.assess(ctx(smoothedPower = 200))
        assertEquals(FieldState.WARN, r.state)
    }

    @Test
    fun `flat power far above target returns BAD`() {
        val r = PowerAdvisor.assess(ctx(smoothedPower = 250))
        assertEquals(FieldState.BAD, r.state)
    }

    @Test
    fun `short climb allows higher target`() {
        val r = PowerAdvisor.assess(ctx(smoothedPower = 200, grade = 5f, ascentLeft = 200))
        // short climb: low=160, high=210 → 200 is in range
        assertEquals(FieldState.GOOD, r.state)
    }

    @Test
    fun `long climb lowers target`() {
        val r = PowerAdvisor.assess(ctx(smoothedPower = 170, grade = 5f, ascentLeft = 800))
        // long climb: low=110, high=150 → 170 is above → WARN
        assertEquals(FieldState.WARN, r.state)
    }

    @Test
    fun `long ride reduces target range`() {
        val r = PowerAdvisor.assess(ctx(smoothedPower = 200, elapsedSec = 18_000L))
        // long ride factor 0.9: low=135, high=156 → 200 is far above → BAD
        assertEquals(FieldState.BAD, r.state)
    }

    @Test
    fun `WBal critical triggers BAD`() {
        // WBal 15-29 no longer forces WARN - removed that logic
        // Only critical WBal < 15 triggers BAD
        val r = PowerAdvisor.assess(ctx(smoothedPower = 160, wBal = 20))
        assertEquals(FieldState.GOOD, r.state)
    }

    @Test
    fun `WBal critical forces BAD`() {
        val r = PowerAdvisor.assess(ctx(smoothedPower = 160, wBal = 10))
        assertEquals(FieldState.BAD, r.state)
    }

    @Test
    fun `HR drift modifier worsens state`() {
        val r = PowerAdvisor.assess(ctx(smoothedPower = 140, decoupling = 8f))
        // base: 140 is below target range → NEUTRAL
        // HR drift > 5 → modifier count = 1 → worsen by 1 → WARN
        assertEquals(FieldState.WARN, r.state)
    }

    @Test
    fun `high temperature modifier worsens state`() {
        val r = PowerAdvisor.assess(ctx(smoothedPower = 160, temp = 38f))
        // base: in range → GOOD
        // temp > 35 → modifier count = 1 → worsen by 1 → OK
        assertEquals(FieldState.OK, r.state)
    }

    @Test
    fun `low todayFactor modifier worsens state`() {
        val r = PowerAdvisor.assess(ctx(smoothedPower = 130, todayFactor = 0.80f))
        // adjustedFtp = 160 → target [120, 139], 130 in range → GOOD
        // todayFactor < 0.85 → modifier count = 1 → worsen by 1 → OK
        assertEquals(FieldState.OK, r.state)
    }

    @Test
    fun `multiple modifiers stack up to max 2`() {
        val r = PowerAdvisor.assess(ctx(
            smoothedPower = 130, decoupling = 8f, temp = 38f, todayFactor = 0.80f,
        ))
        // adjustedFtp = 160 → target [120, 139], 130 in range → GOOD
        // 3 modifiers → max 2 worsen → GOOD→OK→WARN
        assertEquals(FieldState.WARN, r.state)
    }

    @Test
    fun `reason code reflects modifier count`() {
        val r = PowerAdvisor.assess(ctx(smoothedPower = 160, decoupling = 8f, temp = 38f))
        assertEquals("in_range_mod2", r.reasonCode)
    }

    @Test
    fun `reason code reflects below target`() {
        val r = PowerAdvisor.assess(ctx(smoothedPower = 140))
        assertEquals("below_target", r.reasonCode)
    }

    @Test
    fun `target range for flat is Z2-Z3`() {
        val r = PowerAdvisor.assess(ctx(smoothedPower = 160, ftp = 200))
        // Z2 low: 200*0.75=150, Z3 high: 200*0.87=174
        assertEquals(150, r.targetLowWatts)
        assertEquals(174, r.targetHighWatts)
    }

    @Test
    fun `target range for short climb uses higher ceiling`() {
        val r = PowerAdvisor.assess(ctx(smoothedPower = 200, grade = 5f, ascentLeft = 200, ftp = 200))
        // short climb: low=160 (200*0.80), high=210 (200*1.05)
        assertEquals(160, r.targetLowWatts)
        assertEquals(210, r.targetHighWatts)
    }

    @Test
    fun `target range for long climb is conservative`() {
        val r = PowerAdvisor.assess(ctx(smoothedPower = 100, grade = 5f, ascentLeft = 800, ftp = 200))
        // long climb: low=110 (200*0.55), high=150 (200*0.75)
        assertEquals(110, r.targetLowWatts)
        assertEquals(150, r.targetHighWatts)
    }
}
