package com.bikepacking.karoo.message

import com.bikepacking.karoo.field.ClimbContext
import com.bikepacking.karoo.field.ClimbInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class RideMessageCoordinatorTest {

    // ── helpers ────────────────────────────────────────────

    private fun info(
        index: Int = 2, total: Int = 7,
        startDistanceM: Double = 20000.0, lengthM: Double = 500.0,
        totalElevationM: Double = 35.0,
        distanceToStartM: Double = 400.0, distanceToEndM: Double = 900.0,
    ): ClimbInfo = ClimbInfo(
        index = index, total = total,
        startDistanceM = startDistanceM, lengthM = lengthM,
        totalElevationM = totalElevationM,
        distanceToStartM = distanceToStartM, distanceToEndM = distanceToEndM,
        remainingElevationM = if (distanceToStartM > 0) totalElevationM else totalElevationM * 0.5,
        progressOnClimbPercent = if (distanceToStartM > 0) 0.0 else 50.0,
    )

    private fun ctx(
        climbCount: Int = 7,
        active: ClimbInfo? = null, next: ClimbInfo? = null,
        lastCompleted: ClimbInfo? = null,
    ): ClimbContext = ClimbContext(
        climbCount = climbCount,
        activeClimb = active, nextClimb = next,
        lastCompletedClimb = lastCompleted,
    )

    private fun makeCoordinator(
        config: DynamicMessagesConfig = DynamicMessagesConfig(),
    ): RideMessageCoordinator {
        val engine = MessagePriorityEngine(config)
        return RideMessageCoordinator(
            twilightProvider = LightTwilightMessageProvider(),
            climbPacingProvider = ClimbPacingMessageProvider(),
            priorityEngine = engine,
            config = config,
        )
    }

    // ── Test 1: no candidates → null ───────────────────────

    @Test
    fun `no candidates returns null`() {
        val coord = makeCoordinator()
        val msg = coord.selectMessage(
            nowMs = 1_000_000_000L,
            climbContext = ctx(climbCount = 0),
        )
        assertNull(msg)
    }

    // ── Test 2: climb ahead ────────────────────────────────

    @Test
    fun `climb ahead becomes active`() {
        val coord = makeCoordinator()
        val nxt = info(
            index = 2, lengthM = 800.0, totalElevationM = 60.0,
            distanceToStartM = 300.0,
        )
        val msg = coord.selectMessage(
            nowMs = 1_000_000_000L,
            climbContext = ctx(next = nxt),
        )
        assertNotNull(msg)
        assertEquals("CLIMB_AHEAD", msg!!.type)
        assertEquals(MessageSeverity.WARNING, msg.severity)
        assertEquals(RideMessageModule.CLIMB_PACING, msg.module)
    }

    // ── Test 3: twilight alarm beats climb summary ─────────

    @Test
    fun `twilight alarm beats climb summary`() {
        val coord = makeCoordinator()

        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.set(2025, Calendar.DECEMBER, 15, 16, 0, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val nowMs = cal.timeInMillis

        val completed = info(index = 0, lengthM = 800.0, totalElevationM = 54.0)

        val msg = coord.selectMessage(
            nowMs = nowMs,
            climbContext = ctx(lastCompleted = completed),
            latitude = 52.23,
            longitude = 21.01,
            etaMs = null,
            hasRoute = true,
            remainingKm = 50f,
            isRiding = true,
        )
        assertNotNull(msg)
        assertEquals(
            "TWILIGHT_ALARM (ALARM/60) should beat CLIMB_SUMMARY (INFO/40)",
            "TWILIGHT_ALARM", msg!!.type,
        )
    }

    // ── Test 4: disabled climb pacing ──────────────────────

    @Test
    fun `disabled climb pacing hides climb message`() {
        val config = DynamicMessagesConfig(
            moduleEnabled = mapOf(RideMessageModule.CLIMB_PACING to false),
        )
        val coord = makeCoordinator(config)
        val nxt = info(
            index = 2, lengthM = 800.0, totalElevationM = 60.0,
            distanceToStartM = 300.0,
        )
        val msg = coord.selectMessage(
            nowMs = 1_000_000_000L,
            climbContext = ctx(next = nxt),
        )
        assertNull(msg)
    }

    // ── Test 5: disabled dynamic messages ──────────────────

    @Test
    fun `disabled dynamic messages returns null`() {
        val config = DynamicMessagesConfig(enabled = false)
        val coord = makeCoordinator(config)
        val nxt = info(
            index = 2, lengthM = 800.0, totalElevationM = 60.0,
            distanceToStartM = 300.0,
        )
        val msg = coord.selectMessage(
            nowMs = 1_000_000_000L,
            climbContext = ctx(next = nxt),
        )
        assertNull(msg)
    }

    // ── Test 6: minDisplayMs ───────────────────────────────

    @Test
    fun `minDisplayMs keeps active message`() {
        val coord = makeCoordinator()
        val baseMs = 1_000_000_000L
        val nxt = info(
            index = 2, lengthM = 800.0, totalElevationM = 60.0,
            distanceToStartM = 300.0,
        )

        val first = coord.selectMessage(
            nowMs = baseMs,
            climbContext = ctx(next = nxt),
        )
        assertNotNull(first)
        assertEquals("CLIMB_AHEAD", first!!.type)

        val second = coord.selectMessage(
            nowMs = baseMs + 100,
            climbContext = ctx(next = nxt),
        )
        assertNotNull(second)
        assertEquals("CLIMB_AHEAD", second!!.type)

        coord.dismissCurrent()
        val third = coord.selectMessage(
            nowMs = baseMs + 10_000,
            climbContext = ctx(next = nxt),
        )
        assertNull(third)
    }

    // ── Test 7: climb summary ──────────────────────────────

    @Test
    fun `climb summary becomes active`() {
        val coord = makeCoordinator()
        val completed = info(index = 0, lengthM = 800.0, totalElevationM = 54.0)
        val msg = coord.selectMessage(
            nowMs = 1_000_000_000L,
            climbContext = ctx(lastCompleted = completed),
        )
        assertNotNull(msg)
        assertEquals("CLIMB_SUMMARY", msg!!.type)
        assertEquals(MessageSeverity.INFO, msg.severity)
        assertEquals(RideMessageModule.CLIMB_PACING, msg.module)
    }

    // ── Test 8: dismissed active not returned ──────────────

    @Test
    fun `dismissed active not returned after dedup`() {
        val coord = makeCoordinator()
        val completed = info(index = 0, lengthM = 800.0, totalElevationM = 54.0)

        coord.selectMessage(
            nowMs = 1_000_000_000L,
            climbContext = ctx(lastCompleted = completed),
        )
        coord.dismissCurrent()

        val second = coord.selectMessage(
            nowMs = 1_000_000_100L,
            climbContext = ctx(lastCompleted = completed),
        )
        assertNull(second)
    }

    // ── Test 9: CLIMB_HOLD_TARGET with active climb ──────

    @Test
    fun `active climb with FTP generates CLIMB_HOLD_TARGET`() {
        val coord = makeCoordinator()
        val act = info(
            index = 0, total = 5,
            lengthM = 2000.0, totalElevationM = 140.0,
            distanceToStartM = -200.0, distanceToEndM = 1800.0,
        )
        val msg = coord.selectMessage(
            nowMs = 1_000_000_000L,
            climbContext = ctx(active = act),
            ftpW = 275,
            todayFactor = 1.0f,
        )
        assertNotNull(msg)
        assertEquals("CLIMB_HOLD_TARGET", msg!!.type)
        assertEquals(MessageSeverity.INFO, msg.severity)
        assertEquals(RideMessageModule.CLIMB_PACING, msg.module)
    }

    @Test
    fun `CLIMB_HOLD_TARGET beats CLIMB_SUMMARY in priority`() {
        val coord = makeCoordinator()
        val act = info(
            index = 0, total = 5,
            lengthM = 2000.0, totalElevationM = 140.0,
            distanceToStartM = -200.0, distanceToEndM = 1800.0,
        )
        val done = info(
            index = -1, total = 5,
            lengthM = 500.0, totalElevationM = 30.0,
            distanceToStartM = -800.0, distanceToEndM = -300.0,
            startDistanceM = 2000.0,
        )
        val ctxBoth = ClimbContext(
            climbCount = 5,
            activeClimb = act,
            lastCompletedClimb = done,
        )
        val msg = coord.selectMessage(
            nowMs = 1_000_000_000L,
            climbContext = ctxBoth,
            ftpW = 275,
            todayFactor = 1.0f,
        )
        assertNotNull(msg)
        assertEquals(
            "CLIMB_HOLD_TARGET (INFO/45) should beat CLIMB_SUMMARY (INFO/40)",
            "CLIMB_HOLD_TARGET", msg!!.type,
        )
    }

    // ── Test 10: EASE_OFF beats HOLD_TARGET ───────────────

    @Test
    fun `CLIMB_EASE_OFF beats CLIMB_HOLD_TARGET in priority`() {
        val coord = makeCoordinator()
        val act = info(
            index = 0, total = 5,
            lengthM = 2000.0, totalElevationM = 140.0,
            distanceToStartM = -200.0, distanceToEndM = 1800.0,
        )
        val msg = coord.selectMessage(
            nowMs = 1_000_000_000L,
            climbContext = ctx(active = act),
            ftpW = 275,
            todayFactor = 1.0f,
            currentPowerW = 400,
        )
        assertNotNull(msg)
        assertEquals(
            "CLIMB_EASE_OFF (ALARM/70) should beat CLIMB_HOLD_TARGET (INFO/45)",
            "CLIMB_EASE_OFF", msg!!.type,
        )
    }

    // ── Test 11: currentActiveMessage accessor ─────────────

    @Test
    fun `currentActiveMessage returns selected message`() {
        val coord = makeCoordinator()
        assertNull(coord.currentActiveMessage())

        val completed = info(index = 0, lengthM = 800.0, totalElevationM = 54.0)
        coord.selectMessage(
            nowMs = 1_000_000_000L,
            climbContext = ctx(lastCompleted = completed),
        )

        assertNotNull(coord.currentActiveMessage())
        assertEquals("CLIMB_SUMMARY", coord.currentActiveMessage()!!.type)
    }
}
