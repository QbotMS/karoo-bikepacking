package com.bikepacking.karoo.field

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StatsValueFormatterTest {

    private fun assertFv(main: String, unit: String?, actual: StatsFormattedValue) {
        assertEquals("main mismatch", main, actual.main)
        assertEquals("unit mismatch", unit, actual.unit)
    }

    // ── NPW ──
    @Test
    fun `npW shows fallback when zero`() {
        assertFv("--", null, StatsValueFormatter.npW(0))
    }

    @Test
    fun `npW shows fallback when negative`() {
        assertFv("--", null, StatsValueFormatter.npW(-1))
    }

    @Test
    fun `npW formats value and unit`() {
        assertFv("196", "W", StatsValueFormatter.npW(196))
    }

    @Test
    fun `npW caps at 999`() {
        assertFv("999", "W", StatsValueFormatter.npW(1200))
    }

    // ── IF ──
    @Test
    fun `ifValue shows fallback when zero`() {
        assertFv("--", null, StatsValueFormatter.ifValue(0f))
    }

    @Test
    fun `ifValue shows fallback when negative`() {
        assertFv("--", null, StatsValueFormatter.ifValue(-0.1f))
    }

    @Test
    fun `ifValue formats normal`() {
        assertFv("0.78", null, StatsValueFormatter.ifValue(0.78f))
    }

    @Test
    fun `ifValue caps at 1_99`() {
        assertFv("1.99", null, StatsValueFormatter.ifValue(2.5f))
    }

    // ── VI ──
    @Test
    fun `vi shows fallback when zero`() {
        assertFv("--", null, StatsValueFormatter.vi(0f))
    }

    @Test
    fun `vi formats normal`() {
        assertFv("1.05", null, StatsValueFormatter.vi(1.05f))
    }

    // ── CARBS G ──
    @Test
    fun `carbsG shows fallback when zero`() {
        assertFv("--", null, StatsValueFormatter.carbsG(0))
    }

    @Test
    fun `carbsG formats value and unit`() {
        assertFv("60", "g", StatsValueFormatter.carbsG(60))
    }

    // ── FLUID L ──
    @Test
    fun `fluidL shows fallback when zero`() {
        assertFv("--", null, StatsValueFormatter.fluidL(0f))
    }

    @Test
    fun `fluidL formats value and unit`() {
        val fv = StatsValueFormatter.fluidL(0.75f)
        assertEquals("0.8", fv.main)
        assertEquals("L", fv.unit)
    }

    // ── CALORIES ──
    @Test
    fun `calories shows fallback when zero`() {
        assertFv("--", null, StatsValueFormatter.calories(0))
    }

    @Test
    fun `calories formats normal`() {
        assertFv("1250", null, StatsValueFormatter.calories(1250))
    }

    // ── TSS ──
    @Test
    fun `tss shows fallback when zero`() {
        assertFv("--", null, StatsValueFormatter.tss(0f))
    }

    @Test
    fun `tss formats normal`() {
        assertFv("142", null, StatsValueFormatter.tss(142.3f))
    }

    @Test
    fun `tss caps at 9999`() {
        assertFv("9999", null, StatsValueFormatter.tss(15000f))
    }

    // ── DECOUPLING PCT ──
    @Test
    fun `decouplingPct shows fallback when no data`() {
        assertFv("--", null, StatsValueFormatter.decouplingPct(0f, false))
    }

    @Test
    fun `decouplingPct shows fallback when zero`() {
        assertFv("--", null, StatsValueFormatter.decouplingPct(0f, true))
    }

    @Test
    fun `decouplingPct formats positive percent with plus`() {
        val fv = StatsValueFormatter.decouplingPct(4.2f, true)
        assertEquals("+4", fv.main)
        assertEquals("%", fv.unit)
    }

    // ── RESERVE NUMBER ──
    @Test
    fun `reserveNumber formats value and unit`() {
        val fv = StatsValueFormatter.reserveNumber(72)
        assertEquals("72", fv.main)
        assertEquals("%", fv.unit)
    }

    @Test
    fun `reserveNumber formats negative`() {
        val fv = StatsValueFormatter.reserveNumber(-5)
        assertEquals("-5", fv.main)
        assertEquals("%", fv.unit)
    }

    // ── ASCENT M ──
    @Test
    fun `ascentM shows fallback when negative`() {
        assertFv("--", null, StatsValueFormatter.ascentM(-1))
    }

    @Test
    fun `ascentM formats value and unit`() {
        val fv = StatsValueFormatter.ascentM(520)
        assertEquals("520", fv.main)
        assertEquals("m", fv.unit)
    }

    // ── ASCENT LEFT M ──
    @Test
    fun `ascentLeftM shows fallback when no route`() {
        assertFv("--", null, StatsValueFormatter.ascentLeftM(500, false))
    }

    @Test
    fun `ascentLeftM formats value and unit`() {
        val fv = StatsValueFormatter.ascentLeftM(680, true)
        assertEquals("680", fv.main)
        assertEquals("m", fv.unit)
    }

    // ── ETA TIME ──
    @Test
    fun `etaTime shows fallback when zero`() {
        assertFv("--", null, StatsValueFormatter.etaTime(0L))
    }

    @Test
    fun `etaTime returns HHmm format`() {
        val fv = StatsValueFormatter.etaTime(System.currentTimeMillis() + 3600000L)
        assertTrue(fv.main.matches(Regex("\\d{2}:\\d{2}")))
        assertEquals(null, fv.unit)
    }

    // ── AVG ALL ──
    @Test
    fun `avgAll shows fallback when zero`() {
        assertFv("--", null, StatsValueFormatter.avgAll(0f))
    }

    @Test
    fun `avgAll formats normal`() {
        assertFv("22.4", null, StatsValueFormatter.avgAll(22.4f))
    }

    // ── ELAPSED TIME ──
    @Test
    fun `elapsedTime shows fallback when zero`() {
        assertFv("--", null, StatsValueFormatter.elapsedTime(0L))
    }

    @Test
    fun `elapsedTime formats hours and minutes`() {
        assertFv("2:18", null, StatsValueFormatter.elapsedTime(8280L))
    }

    // ── STOP TIME ──
    @Test
    fun `stopTime shows fallback when negative`() {
        assertFv("--", null, StatsValueFormatter.stopTime(-1L))
    }

    @Test
    fun `stopTime formats zero`() {
        assertFv("0:00", null, StatsValueFormatter.stopTime(0L))
    }

    @Test
    fun `stopTime formats minutes`() {
        assertFv("0:18", null, StatsValueFormatter.stopTime(1080L))
    }

    @Test
    fun `stopTime formats over one hour`() {
        assertFv("1:05", null, StatsValueFormatter.stopTime(3900L))
    }

    // ── BATTERY PER HOUR ──
    @Test
    fun `batteryPerHour shows fallback when zero`() {
        assertFv("--", null, StatsValueFormatter.batteryPerHour(0f))
    }

    @Test
    fun `batteryPerHour formats value and unit`() {
        val fv = StatsValueFormatter.batteryPerHour(8.3f)
        assertEquals("8", fv.main)
        assertEquals("%/h", fv.unit)
    }

    // ── BATTERY RUNTIME ──
    @Test
    fun `batteryRuntime shows fallback when zero`() {
        assertFv("--", null, StatsValueFormatter.batteryRuntime(0L))
    }

    @Test
    fun `batteryRuntime formats hours and minutes`() {
        assertFv("7:20", null, StatsValueFormatter.batteryRuntime(26400L))
    }

    // ── BATTERY SIMPLE ──
    @Test
    fun `batterySimple shows fallback when zero`() {
        assertFv("--", null, StatsValueFormatter.batterySimple(0))
    }

    @Test
    fun `batterySimple formats value and unit`() {
        val fv = StatsValueFormatter.batterySimple(83)
        assertEquals("83", fv.main)
        assertEquals("%", fv.unit)
    }

    // ── RD BAT ──
    @Test
    fun `rdBat shows fallback when null`() {
        assertFv("--", null, StatsValueFormatter.rdBat(null))
    }

    @Test
    fun `rdBat shows fallback when zero`() {
        assertFv("--", null, StatsValueFormatter.rdBat(0))
    }

    @Test
    fun `rdBat formats value and unit`() {
        val fv = StatsValueFormatter.rdBat(83)
        assertEquals("83", fv.main)
        assertEquals("%", fv.unit)
    }

    @Test
    fun `rdBat does not use head unit battery`() {
        assertFv("--", null, StatsValueFormatter.rdBat(null))
        val fv = StatsValueFormatter.rdBat(83)
        assertEquals("83", fv.main)
        assertEquals("%", fv.unit)
    }

    // ── FORMAT TIME ──
    @Test
    fun `formatTime formats zero`() {
        assertEquals("0:00", StatsValueFormatter.formatTime(0L))
    }

    @Test
    fun `formatTime formats minutes`() {
        assertEquals("0:30", StatsValueFormatter.formatTime(1800L))
    }

    @Test
    fun `formatTime formats hours and minutes`() {
        assertEquals("3:45", StatsValueFormatter.formatTime(13500L))
    }

    @Test
    fun `formatTime caps at 99_59`() {
        assertEquals("99:59", StatsValueFormatter.formatTime(500000L))
    }

    @Test
    fun `formatTime fallback on negative`() {
        assertEquals("--:--", StatsValueFormatter.formatTime(-1L))
    }
}
