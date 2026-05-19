package com.bikepacking.karoo.message

import com.bikepacking.karoo.field.ClimbContext
import com.bikepacking.karoo.field.ClimbDifficulty
import com.bikepacking.karoo.field.ClimbInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClimbPacingMessageProviderTest {

    private val nowMs = 1_000_000_000L

    // ── helpers ────────────────────────────────────────────

    private fun info(
        index: Int = 2,
        total: Int = 7,
        startDistanceM: Double = 20000.0,
        lengthM: Double = 500.0,
        totalElevationM: Double = 35.0,
        distanceToStartM: Double = 400.0,
        distanceToEndM: Double = 900.0,
    ): ClimbInfo = ClimbInfo(
        index = index,
        total = total,
        startDistanceM = startDistanceM,
        lengthM = lengthM,
        totalElevationM = totalElevationM,
        distanceToStartM = distanceToStartM,
        distanceToEndM = distanceToEndM,
        remainingElevationM = if (distanceToStartM > 0) totalElevationM else totalElevationM * 0.5,
        progressOnClimbPercent = if (distanceToStartM > 0) 0.0 else 50.0,
    )

    private fun ctx(
        climbCount: Int = 7,
        active: ClimbInfo? = null,
        next: ClimbInfo? = null,
        lastCompleted: ClimbInfo? = null,
    ): ClimbContext = ClimbContext(
        climbCount = climbCount,
        activeClimb = active,
        nextClimb = next,
        lastCompletedClimb = lastCompleted,
    )

    // ── advanceDistanceM ───────────────────────────────────

    @Test
    fun `advanceDistanceM flat or rolling`() {
        val prov = ClimbPacingMessageProvider()
        assertEquals(250.0, prov.advanceDistanceM(info(lengthM = 1000.0, totalElevationM = 10.0)), 0.001)
    }

    @Test
    fun `advanceDistanceM moderate`() {
        val prov = ClimbPacingMessageProvider()
        // 5% grade → MODERATE
        assertEquals(500.0, prov.advanceDistanceM(info(lengthM = 1000.0, totalElevationM = 50.0)), 0.001)
    }

    @Test
    fun `advanceDistanceM steep`() {
        val prov = ClimbPacingMessageProvider()
        // 9% grade → STEEP
        assertEquals(800.0, prov.advanceDistanceM(info(lengthM = 1000.0, totalElevationM = 90.0)), 0.001)
    }

    @Test
    fun `advanceDistanceM very steep`() {
        val prov = ClimbPacingMessageProvider()
        // 12% grade → VERY_STEEP
        assertEquals(800.0, prov.advanceDistanceM(info(lengthM = 1000.0, totalElevationM = 120.0)), 0.001)
    }

    @Test
    fun `advanceDistanceM flat grade`() {
        val prov = ClimbPacingMessageProvider()
        assertEquals(250.0, prov.advanceDistanceM(info(lengthM = 1000.0, totalElevationM = 10.0)), 0.001)
    }

    // ── empty / no climbs ──────────────────────────────────

    @Test
    fun `no climbs returns no messages`() {
        val prov = ClimbPacingMessageProvider()
        val msgs = prov.generate(ctx(climbCount = 0), nowMs)
        assertTrue(msgs.isEmpty())
    }

    @Test
    fun `empty context returns no messages`() {
        val prov = ClimbPacingMessageProvider()
        val msgs = prov.generate(ClimbContext(), nowMs)
        assertTrue(msgs.isEmpty())
    }

    // ── CLIMB_AHEAD ────────────────────────────────────────

    @Test
    fun `next climb inside threshold generates CLIMB_AHEAD`() {
        val prov = ClimbPacingMessageProvider()
        val nxt = info(index = 2, lengthM = 800.0, totalElevationM = 60.0, distanceToStartM = 300.0)
        val msgs = prov.generate(ctx(next = nxt), nowMs)
        assertEquals(1, msgs.size)
        assertEquals("CLIMB_AHEAD", msgs[0].type)
    }

    @Test
    fun `next climb outside threshold no CLIMB_AHEAD`() {
        val prov = ClimbPacingMessageProvider()
        val nxt = info(index = 2, lengthM = 800.0, totalElevationM = 60.0, distanceToStartM = 900.0)
        val msgs = prov.generate(ctx(next = nxt), nowMs)
        assertTrue(msgs.isEmpty())
    }

    @Test
    fun `CLIMB_AHEAD format contains PODJAZD and label`() {
        val prov = ClimbPacingMessageProvider()
        val nxt = info(index = 2, total = 7, lengthM = 800.0, totalElevationM = 60.0, distanceToStartM = 300.0)
        val msgs = prov.generate(ctx(next = nxt), nowMs)
        assertEquals("PODJAZD 3/7", msgs[0].line1)
    }

    @Test
    fun `CLIMB_AHEAD line2 contains length and grade`() {
        val prov = ClimbPacingMessageProvider()
        val nxt = info(index = 0, total = 3, lengthM = 850.0, totalElevationM = 51.0, distanceToStartM = 200.0)
        val msgs = prov.generate(ctx(next = nxt), nowMs)
        assertEquals("850m · 6%", msgs[0].line2)
    }

    @Test
    fun `active climb no CLIMB_AHEAD even with nextClimb`() {
        val prov = ClimbPacingMessageProvider()
        val act = info(index = 0, distanceToStartM = -200.0, distanceToEndM = 600.0)
        val nxt = info(index = 1, distanceToStartM = 300.0)
        val msgs = prov.generate(ctx(active = act, next = nxt), nowMs)
        val ahead = msgs.filter { it.type == "CLIMB_AHEAD" }
        assertTrue(ahead.isEmpty())
    }

    @Test
    fun `CLIMB_AHEAD dedup for same climb`() {
        val prov = ClimbPacingMessageProvider()
        val nxt = info(index = 2, lengthM = 800.0, totalElevationM = 60.0, distanceToStartM = 300.0)
        val first = prov.generate(ctx(next = nxt), nowMs)
        assertEquals(1, first.size)

        val second = prov.generate(ctx(next = nxt), nowMs)
        val ahead = second.filter { it.type == "CLIMB_AHEAD" }
        assertTrue(ahead.isEmpty())
    }

    @Test
    fun `different next climb can generate new CLIMB_AHEAD`() {
        val prov = ClimbPacingMessageProvider()
        val nxt0 = info(index = 2, lengthM = 800.0, totalElevationM = 60.0, distanceToStartM = 300.0)
        prov.generate(ctx(next = nxt0), nowMs)

        val nxt1 = info(index = 3, lengthM = 1000.0, totalElevationM = 80.0, distanceToStartM = 200.0)
        val msgs = prov.generate(ctx(next = nxt1), nowMs)
        val ahead = msgs.filter { it.type == "CLIMB_AHEAD" }
        assertEquals(1, ahead.size)
        assertEquals("PODJAZD 4/7", ahead[0].line1)
    }

    @Test
    fun `negative distance to start does not trigger ahead`() {
        val prov = ClimbPacingMessageProvider()
        val nxt = info(index = 1, distanceToStartM = -100.0)
        val msgs = prov.generate(ctx(next = nxt), nowMs)
        val ahead = msgs.filter { it.type == "CLIMB_AHEAD" }
        assertTrue(ahead.isEmpty())
    }

    // ── CLIMB_SUMMARY ──────────────────────────────────────

    @Test
    fun `completed climb generates CLIMB_SUMMARY`() {
        val prov = ClimbPacingMessageProvider()
        val done = info(index = 0, total = 5, lengthM = 800.0, totalElevationM = 54.0)
        val msgs = prov.generate(ctx(lastCompleted = done), nowMs)
        assertEquals(1, msgs.size)
        assertEquals("CLIMB_SUMMARY", msgs[0].type)
    }

    @Test
    fun `CLIMB_SUMMARY format contains label and OK`() {
        val prov = ClimbPacingMessageProvider()
        val done = info(index = 0, total = 5, lengthM = 800.0, totalElevationM = 54.0)
        val msgs = prov.generate(ctx(lastCompleted = done), nowMs)
        assertEquals("1/5 OK", msgs[0].line1)
    }

    @Test
    fun `CLIMB_SUMMARY line2 contains elevation gain`() {
        val prov = ClimbPacingMessageProvider()
        val done = info(index = 0, lengthM = 800.0, totalElevationM = 54.0)
        val msgs = prov.generate(ctx(lastCompleted = done), nowMs)
        assertEquals("+54m", msgs[0].line2)
    }

    @Test
    fun `CLIMB_SUMMARY includes remaining ascent when provided`() {
        val prov = ClimbPacingMessageProvider()
        val done = info(index = 0, lengthM = 800.0, totalElevationM = 54.0)
        val msgs = prov.generate(ctx(lastCompleted = done), nowMs, routeAscentRemainingM = 420.0)
        assertEquals("+54m · zostało 420m", msgs[0].line2)
    }

    @Test
    fun `CLIMB_SUMMARY works without remaining ascent`() {
        val prov = ClimbPacingMessageProvider()
        val done = info(index = 0, lengthM = 800.0, totalElevationM = 54.0)
        val msgs = prov.generate(ctx(lastCompleted = done), nowMs, routeAscentRemainingM = null)
        assertEquals("+54m", msgs[0].line2)
    }

    @Test
    fun `CLIMB_SUMMARY dedup for same climb`() {
        val prov = ClimbPacingMessageProvider()
        val done = info(index = 0, lengthM = 800.0, totalElevationM = 54.0)
        val first = prov.generate(ctx(lastCompleted = done), nowMs)
        assertEquals(1, first.size)

        val second = prov.generate(ctx(lastCompleted = done), nowMs)
        val summary = second.filter { it.type == "CLIMB_SUMMARY" }
        assertTrue(summary.isEmpty())
    }

    // ── severity, module, priority ─────────────────────────

    @Test
    fun `CLIMB_AHEAD severity module and priority correct`() {
        val prov = ClimbPacingMessageProvider()
        val nxt = info(index = 1, lengthM = 800.0, totalElevationM = 60.0, distanceToStartM = 300.0)
        val msgs = prov.generate(ctx(next = nxt), nowMs)
        assertEquals(MessageSeverity.WARNING, msgs[0].severity)
        assertEquals(RideMessageModule.CLIMB_PACING, msgs[0].module)
        assertEquals("CLIMB_AHEAD", msgs[0].type)
        assertEquals(50, msgs[0].priority)
        assertEquals(8000L, msgs[0].minDisplayMs)
        assertEquals(60000L, msgs[0].cooldownMs)
    }

    @Test
    fun `CLIMB_SUMMARY severity module and priority correct`() {
        val prov = ClimbPacingMessageProvider()
        val done = info(index = 0, lengthM = 800.0, totalElevationM = 54.0)
        val msgs = prov.generate(ctx(lastCompleted = done), nowMs)
        assertEquals(MessageSeverity.INFO, msgs[0].severity)
        assertEquals(RideMessageModule.CLIMB_PACING, msgs[0].module)
        assertEquals("CLIMB_SUMMARY", msgs[0].type)
        assertEquals(40, msgs[0].priority)
        assertEquals(8000L, msgs[0].minDisplayMs)
        assertEquals(60000L, msgs[0].cooldownMs)
    }

    @Test
    fun `CLIMB_SUMMARY shows ZA MOCNO when EASE_OFF was triggered`() {
        val prov = ClimbPacingMessageProvider()
        val act = info(index = 0, distanceToStartM = -200.0, distanceToEndM = 800.0)
        prov.generate(
            climbContext = ctx(active = act), nowMs = nowMs,
            ftpW = 275, todayFactor = 1.0f, currentPowerW = 400,
        )

        val done = info(index = 0, total = 5, lengthM = 800.0, totalElevationM = 54.0,
            distanceToStartM = -1000.0, distanceToEndM = -200.0)
        val msgs = prov.generate(ctx(lastCompleted = done), nowMs)
        val summary = msgs.filter { it.type == "CLIMB_SUMMARY" }
        assertEquals(1, summary.size)
        assertEquals("1/5 ZA MOCNO", summary[0].line1)
    }

    @Test
    fun `CLIMB_SUMMARY severity WARNING and priority 45 for ZA MOCNO`() {
        val prov = ClimbPacingMessageProvider()
        val act = info(index = 0, distanceToStartM = -200.0, distanceToEndM = 800.0)
        prov.generate(
            climbContext = ctx(active = act), nowMs = nowMs,
            ftpW = 275, todayFactor = 1.0f, currentPowerW = 400,
        )

        val done = info(index = 0, total = 5, lengthM = 800.0, totalElevationM = 54.0,
            distanceToStartM = -1000.0, distanceToEndM = -200.0)
        val msgs = prov.generate(ctx(lastCompleted = done), nowMs)
        val summary = msgs.filter { it.type == "CLIMB_SUMMARY" }
        assertEquals(1, summary.size)
        assertEquals(MessageSeverity.WARNING, summary[0].severity)
        assertEquals(45, summary[0].priority)
    }

    // ── resetState ─────────────────────────────────────────

    @Test
    fun `resetState clears dedup tracking`() {
        val prov = ClimbPacingMessageProvider()
        val nxt = info(index = 0, lengthM = 800.0, totalElevationM = 60.0, distanceToStartM = 300.0)
        prov.generate(ctx(next = nxt), nowMs)
        assertEquals(0, prov.generate(ctx(next = nxt), nowMs).size)

        prov.resetState()
        val after = prov.generate(ctx(next = nxt), nowMs)
        assertEquals(1, after.size)
    }

    // ── CLIMB_HOLD_TARGET ──────────────────────────────────

    @Test
    fun `active climb with target generates CLIMB_HOLD_TARGET`() {
        val prov = ClimbPacingMessageProvider()
        val act = info(
            index = 0, total = 5,
            lengthM = 2000.0, totalElevationM = 140.0,
            distanceToStartM = -200.0, distanceToEndM = 1800.0,
        )
        val msgs = prov.generate(
            climbContext = ctx(active = act),
            nowMs = nowMs,
            ftpW = 275,
            todayFactor = 1.0f,
        )
        val target = msgs.filter { it.type == "CLIMB_HOLD_TARGET" }
        assertEquals(1, target.size)
    }

    @Test
    fun `no active climb does not generate CLIMB_HOLD_TARGET`() {
        val prov = ClimbPacingMessageProvider()
        val msgs = prov.generate(
            climbContext = ctx(climbCount = 0),
            nowMs = nowMs,
            ftpW = 275,
            todayFactor = 1.0f,
        )
        val target = msgs.filter { it.type == "CLIMB_HOLD_TARGET" }
        assertTrue(target.isEmpty())
    }

    @Test
    fun `missing FTP does not generate CLIMB_HOLD_TARGET`() {
        val prov = ClimbPacingMessageProvider()
        val act = info(
            index = 0, total = 5,
            lengthM = 2000.0, totalElevationM = 140.0,
            distanceToStartM = -200.0, distanceToEndM = 1800.0,
        )
        val msgs = prov.generate(
            climbContext = ctx(active = act),
            nowMs = nowMs,
            ftpW = 0,
            todayFactor = 1.0f,
        )
        val target = msgs.filter { it.type == "CLIMB_HOLD_TARGET" }
        assertTrue(target.isEmpty())
    }

    @Test
    fun `CLIMB_HOLD_TARGET line2 contains target range`() {
        val prov = ClimbPacingMessageProvider()
        val act = info(
            index = 2, total = 7,
            lengthM = 2000.0, totalElevationM = 140.0,
            distanceToStartM = -200.0, distanceToEndM = 1800.0,
        )
        val msgs = prov.generate(
            climbContext = ctx(active = act),
            nowMs = nowMs,
            ftpW = 275,
            todayFactor = 1.0f,
        )
        val target = msgs.filter { it.type == "CLIMB_HOLD_TARGET" }
        assertEquals(1, target.size)
        assertTrue(target[0].line2.contains("TRZYMAJ"))
        assertTrue(target[0].line2.contains("W"))
    }

    @Test
    fun `CLIMB_HOLD_TARGET line1 contains label and grade`() {
        val prov = ClimbPacingMessageProvider()
        val act = info(
            index = 2, total = 7,
            lengthM = 2000.0, totalElevationM = 140.0,
            distanceToStartM = -200.0, distanceToEndM = 1800.0,
        )
        val msgs = prov.generate(
            climbContext = ctx(active = act),
            nowMs = nowMs,
            ftpW = 275,
            todayFactor = 1.0f,
        )
        val target = msgs.filter { it.type == "CLIMB_HOLD_TARGET" }
        assertEquals(1, target.size)
        assertEquals("3/7 · 7%", target[0].line1)
    }

    @Test
    fun `CLIMB_HOLD_TARGET dedup once per climb`() {
        val prov = ClimbPacingMessageProvider()
        val act = info(
            index = 0, total = 5,
            lengthM = 2000.0, totalElevationM = 140.0,
            distanceToStartM = -200.0, distanceToEndM = 1800.0,
        )
        val first = prov.generate(
            climbContext = ctx(active = act),
            nowMs = nowMs,
            ftpW = 275,
            todayFactor = 1.0f,
        )
        val target1 = first.filter { it.type == "CLIMB_HOLD_TARGET" }
        assertEquals(1, target1.size)

        val second = prov.generate(
            climbContext = ctx(active = act),
            nowMs = nowMs,
            ftpW = 275,
            todayFactor = 1.0f,
        )
        val target2 = second.filter { it.type == "CLIMB_HOLD_TARGET" }
        assertTrue(target2.isEmpty())
    }

    @Test
    fun `CLIMB_HOLD_TARGET severity module and priority correct`() {
        val prov = ClimbPacingMessageProvider()
        val act = info(
            index = 0, total = 5,
            lengthM = 2000.0, totalElevationM = 140.0,
            distanceToStartM = -200.0, distanceToEndM = 1800.0,
        )
        val msgs = prov.generate(
            climbContext = ctx(active = act),
            nowMs = nowMs,
            ftpW = 275,
            todayFactor = 1.0f,
        )
        val target = msgs.filter { it.type == "CLIMB_HOLD_TARGET" }
        assertEquals(1, target.size)
        assertEquals(MessageSeverity.INFO, target[0].severity)
        assertEquals(RideMessageModule.CLIMB_PACING, target[0].module)
        assertEquals(45, target[0].priority)
        assertEquals(8000L, target[0].minDisplayMs)
        assertEquals(120000L, target[0].cooldownMs)
    }

    @Test
    fun `resetState clears CLIMB_HOLD_TARGET dedup`() {
        val prov = ClimbPacingMessageProvider()
        val act = info(
            index = 0, total = 5,
            lengthM = 2000.0, totalElevationM = 140.0,
            distanceToStartM = -200.0, distanceToEndM = 1800.0,
        )
        prov.generate(
            climbContext = ctx(active = act),
            nowMs = nowMs,
            ftpW = 275,
            todayFactor = 1.0f,
        )
        val firstRetry = prov.generate(
            climbContext = ctx(active = act),
            nowMs = nowMs,
            ftpW = 275,
            todayFactor = 1.0f,
        )
        val target1 = firstRetry.filter { it.type == "CLIMB_HOLD_TARGET" }
        assertTrue(target1.isEmpty())

        prov.resetState()
        val afterReset = prov.generate(
            climbContext = ctx(active = act),
            nowMs = nowMs,
            ftpW = 275,
            todayFactor = 1.0f,
        )
        val target2 = afterReset.filter { it.type == "CLIMB_HOLD_TARGET" }
        assertEquals(1, target2.size)
    }

    @Test
    fun `CLIMB_HOLD_TARGET can appear alongside ahead and summary`() {
        val prov = ClimbPacingMessageProvider()
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
        val msgs = prov.generate(
            climbContext = ctx(active = act, lastCompleted = done),
            nowMs = nowMs,
            ftpW = 275,
            todayFactor = 1.0f,
        )
        val types = msgs.map { it.type }
        assertTrue("expected CLIMB_HOLD_TARGET", "CLIMB_HOLD_TARGET" in types)
        assertTrue("expected CLIMB_SUMMARY", "CLIMB_SUMMARY" in types)
    }

    // ── CLIMB_EASE_OFF ────────────────────────────────────

    @Test
    fun `no active climb does not generate EASE_OFF`() {
        val prov = ClimbPacingMessageProvider()
        val msgs = prov.generate(
            climbContext = ctx(climbCount = 0),
            nowMs = nowMs,
            ftpW = 275,
            todayFactor = 1.0f,
            currentPowerW = 400,
        )
        val easeOff = msgs.filter { it.type == "CLIMB_EASE_OFF" }
        assertTrue(easeOff.isEmpty())
    }

    @Test
    fun `missing power does not generate EASE_OFF`() {
        val prov = ClimbPacingMessageProvider()
        val act = info(index = 0, distanceToStartM = -200.0, distanceToEndM = 800.0)
        val msgs = prov.generate(
            climbContext = ctx(active = act),
            nowMs = nowMs,
            ftpW = 275,
            todayFactor = 1.0f,
            currentPowerW = 0,
        )
        val easeOff = msgs.filter { it.type == "CLIMB_EASE_OFF" }
        assertTrue(easeOff.isEmpty())
    }

    @Test
    fun `power below targetHigh does not generate EASE_OFF`() {
        val prov = ClimbPacingMessageProvider()
        val act = info(index = 0, distanceToStartM = -200.0, distanceToEndM = 800.0)
        val msgs = prov.generate(
            climbContext = ctx(active = act),
            nowMs = nowMs,
            ftpW = 275,
            todayFactor = 1.0f,
            currentPowerW = 200,
        )
        val easeOff = msgs.filter { it.type == "CLIMB_EASE_OFF" }
        assertTrue(easeOff.isEmpty())
    }

    @Test
    fun `power clearly above targetHigh generates EASE_OFF`() {
        val prov = ClimbPacingMessageProvider()
        val act = info(index = 0, distanceToStartM = -200.0, distanceToEndM = 800.0)
        val msgs = prov.generate(
            climbContext = ctx(active = act),
            nowMs = nowMs,
            ftpW = 275,
            todayFactor = 1.0f,
            currentPowerW = 400,
        )
        val easeOff = msgs.filter { it.type == "CLIMB_EASE_OFF" }
        assertEquals(1, easeOff.size)
    }

    @Test
    fun `Wbal low lowers EASE_OFF threshold`() {
        val prov = ClimbPacingMessageProvider()
        val act = info(index = 0, distanceToStartM = -200.0, distanceToEndM = 800.0)
        val msgs = prov.generate(
            climbContext = ctx(active = act),
            nowMs = nowMs,
            ftpW = 275,
            todayFactor = 1.0f,
            wBalPercent = 40,
            currentPowerW = 290,
        )
        val easeOff = msgs.filter { it.type == "CLIMB_EASE_OFF" }
        assertEquals(1, easeOff.size)
    }

    @Test
    fun `near end of climb does not generate EASE_OFF`() {
        val prov = ClimbPacingMessageProvider()
        val act = info(index = 0, distanceToStartM = -200.0, distanceToEndM = 100.0)
        val msgs = prov.generate(
            climbContext = ctx(active = act),
            nowMs = nowMs,
            ftpW = 275,
            todayFactor = 1.0f,
            currentPowerW = 400,
        )
        val easeOff = msgs.filter { it.type == "CLIMB_EASE_OFF" }
        assertTrue(easeOff.isEmpty())
    }

    @Test
    fun `route almost finished does not generate EASE_OFF`() {
        val prov = ClimbPacingMessageProvider()
        val act = info(index = 0, distanceToStartM = -200.0, distanceToEndM = 800.0)
        val msgs = prov.generate(
            climbContext = ctx(active = act),
            nowMs = nowMs,
            ftpW = 275,
            todayFactor = 1.0f,
            currentPowerW = 400,
            routeDistanceRemainingKm = 1f,
        )
        val easeOff = msgs.filter { it.type == "CLIMB_EASE_OFF" }
        assertTrue(easeOff.isEmpty())
    }

    @Test
    fun `EASE_OFF severity module and priority correct`() {
        val prov = ClimbPacingMessageProvider()
        val act = info(index = 0, distanceToStartM = -200.0, distanceToEndM = 800.0)
        val msgs = prov.generate(
            climbContext = ctx(active = act),
            nowMs = nowMs,
            ftpW = 275,
            todayFactor = 1.0f,
            currentPowerW = 400,
        )
        val easeOff = msgs.filter { it.type == "CLIMB_EASE_OFF" }
        assertEquals(1, easeOff.size)
        assertEquals(MessageSeverity.ALARM, easeOff[0].severity)
        assertEquals(RideMessageModule.CLIMB_PACING, easeOff[0].module)
        assertEquals(70, easeOff[0].priority)
        assertEquals(8000L, easeOff[0].minDisplayMs)
        assertEquals(60000L, easeOff[0].cooldownMs)
    }

    @Test
    fun `EASE_OFF line1 is ODPUŚĆ exclamation`() {
        val prov = ClimbPacingMessageProvider()
        val act = info(index = 0, distanceToStartM = -200.0, distanceToEndM = 800.0)
        val msgs = prov.generate(
            climbContext = ctx(active = act),
            nowMs = nowMs,
            ftpW = 275,
            todayFactor = 1.0f,
            currentPowerW = 400,
        )
        val easeOff = msgs.filter { it.type == "CLIMB_EASE_OFF" }
        assertEquals(1, easeOff.size)
        assertEquals("ODPUŚĆ!", easeOff[0].line1)
    }

    @Test
    fun `EASE_OFF line2 contains target range`() {
        val prov = ClimbPacingMessageProvider()
        val act = info(index = 0, distanceToStartM = -200.0, distanceToEndM = 800.0)
        val msgs = prov.generate(
            climbContext = ctx(active = act),
            nowMs = nowMs,
            ftpW = 275,
            todayFactor = 1.0f,
            currentPowerW = 400,
        )
        val easeOff = msgs.filter { it.type == "CLIMB_EASE_OFF" }
        assertEquals(1, easeOff.size)
        assertTrue(easeOff[0].line2.startsWith("CEL "))
        assertTrue(easeOff[0].line2.contains("W"))
        assertTrue(easeOff[0].line2.contains("–"))
    }

    @Test
    fun `cooldown blocks repeated EASE_OFF for same climb`() {
        val prov = ClimbPacingMessageProvider()
        val act = info(index = 0, distanceToStartM = -200.0, distanceToEndM = 800.0)
        val first = prov.generate(
            climbContext = ctx(active = act), nowMs = nowMs,
            ftpW = 275, todayFactor = 1.0f, currentPowerW = 400,
        )
        assertEquals(1, first.filter { it.type == "CLIMB_EASE_OFF" }.size)

        val second = prov.generate(
            climbContext = ctx(active = act), nowMs = nowMs + 10_000,
            ftpW = 275, todayFactor = 1.0f, currentPowerW = 400,
        )
        val easeOff2 = second.filter { it.type == "CLIMB_EASE_OFF" }
        assertTrue("cooldown should block", easeOff2.isEmpty())
    }

    @Test
    fun `cooldown expiry allows EASE_OFF again`() {
        val prov = ClimbPacingMessageProvider()
        val act = info(index = 0, distanceToStartM = -200.0, distanceToEndM = 800.0)
        prov.generate(
            climbContext = ctx(active = act), nowMs = nowMs,
            ftpW = 275, todayFactor = 1.0f, currentPowerW = 400,
        )

        val later = prov.generate(
            climbContext = ctx(active = act), nowMs = nowMs + 120_000,
            ftpW = 275, todayFactor = 1.0f, currentPowerW = 400,
        )
        val easeOff = later.filter { it.type == "CLIMB_EASE_OFF" }
        assertEquals("cooldown expired → should fire again", 1, easeOff.size)
    }

    // ── combined scenarios ─────────────────────────────────

    @Test
    fun `both ahead and summary can appear in same tick`() {
        val prov = ClimbPacingMessageProvider()
        val nxt = info(index = 3, lengthM = 800.0, totalElevationM = 60.0, distanceToStartM = 300.0)
        val done = info(index = 1, lengthM = 500.0, totalElevationM = 30.0)
        val msgs = prov.generate(ctx(next = nxt, lastCompleted = done), nowMs)
        assertEquals(2, msgs.size)
        val types = msgs.map { it.type }
        assertTrue("CLIMB_AHEAD" in types)
        assertTrue("CLIMB_SUMMARY" in types)
    }
}
