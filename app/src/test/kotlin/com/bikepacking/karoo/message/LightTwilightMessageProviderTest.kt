package com.bikepacking.karoo.message

import com.bikepacking.karoo.SunCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LightTwilightMessageProviderTest {

    private val provider = LightTwilightMessageProvider()
    private val lat = 52.23
    private val lon = 21.01

    // Test date: 2025-09-22 00:00 UTC (autumn equinox — reliable dawn/dusk)
    private val daytimeMs = 1_758_614_400_000L

    @Before
    fun setUp() {
        provider.resetDailyTrack()
    }

    @Test
    fun `warning 30 min before civil dusk`() {
        val duskMs = SunCalculator.civilDuskMs(lat, lon, daytimeMs)
        val nowMs = duskMs - 30 * 60_000L

        val msgs = provider.generate(nowMs, lat, lon, etaMs = null, hasRoute = true, remainingKm = 30f, isRiding = true)
        val dusk = msgs.firstOrNull { it.type == "TWILIGHT_WARNING_30" }
        assertTrue("Expected TWILIGHT_WARNING_30 at 30 min before dusk", dusk != null)
        assertEquals("ZMIERZCH ZA 30M", dusk!!.line1)
        assertEquals("WŁĄCZ LAMPĘ", dusk.line2)
        assertEquals(MessageSeverity.WARNING, dusk.severity)
    }

    @Test
    fun `no warning if ETA ends before dusk with 15 min margin`() {
        val duskMs = SunCalculator.civilDuskMs(lat, lon, daytimeMs)
        val nowMs = duskMs - 30 * 60_000L
        val etaMs = duskMs - 20 * 60_000L  // finishes 20 min before dusk

        val msgs = provider.generate(nowMs, lat, lon, etaMs = etaMs, hasRoute = true, remainingKm = 5f, isRiding = true)
        val dusk = msgs.firstOrNull { it.type == "TWILIGHT_WARNING_30" }
        assertTrue("Should NOT warn if ETA ends >=15 min before dusk", dusk == null)
    }

    @Test
    fun `alarm after civil dusk if riding`() {
        val duskMs = SunCalculator.civilDuskMs(lat, lon, daytimeMs)
        val nowMs = duskMs + 5 * 60_000L

        val msgs = provider.generate(nowMs, lat, lon, etaMs = null, hasRoute = true, remainingKm = 10f, isRiding = true)
        val alarm = msgs.firstOrNull { it.type == "TWILIGHT_ALARM" }
        assertTrue("Expected TWILIGHT_ALARM after civil dusk when riding", alarm != null)
        assertEquals("JUŻ PO ZMIERZCHU", alarm!!.line1)
        assertEquals("SPRAWDŹ LAMPĘ", alarm.line2)
        assertEquals(MessageSeverity.ALARM, alarm.severity)
    }

    @Test
    fun `no alarm after dusk if not riding`() {
        val duskMs = SunCalculator.civilDuskMs(lat, lon, daytimeMs)
        val nowMs = duskMs + 10 * 60_000L

        val msgs = provider.generate(nowMs, lat, lon, etaMs = null, hasRoute = true, remainingKm = 10f, isRiding = false)
        val alarm = msgs.firstOrNull { it.type == "TWILIGHT_ALARM" }
        assertTrue("Should NOT alarm if not riding", alarm == null)
    }

    @Test
    fun `dawn info before civil dawn`() {
        val noonMs = (daytimeMs / 86_400_000L) * 86_400_000L + 43_200_000L
        val dawnMs = SunCalculator.civilDawnMs(lat, lon, noonMs)
        val nowMs = dawnMs - 30 * 60_000L

        val msgs = provider.generate(nowMs, lat, lon, etaMs = null, hasRoute = true, remainingKm = 30f, isRiding = true)
        val dawn = msgs.firstOrNull { it.type == "DAWN_INFO" }
        assertTrue("Expected DAWN_INFO at 30 min before civil dawn", dawn != null)
        assertEquals("ŚWIT ZA 30M", dawn!!.line1)
        assertEquals("ŚWIATŁO WRACA", dawn.line2)
        assertEquals(MessageSeverity.INFO, dawn.severity)
    }

    @Test
    fun `no duplicate dusk warning for same day`() {
        val duskMs = SunCalculator.civilDuskMs(lat, lon, daytimeMs)
        val nowMs = duskMs - 30 * 60_000L

        val first = provider.generate(nowMs, lat, lon, etaMs = null, hasRoute = true, remainingKm = 30f, isRiding = true)
        assertTrue("First call should generate warning", first.any { it.type == "TWILIGHT_WARNING_30" })

        val second = provider.generate(nowMs + 5000L, lat, lon, etaMs = null, hasRoute = true, remainingKm = 30f, isRiding = true)
        val repeat = second.firstOrNull { it.type == "TWILIGHT_WARNING_30" }
        assertTrue("Second call should NOT duplicate warning for same day", repeat == null)
    }

    @Test
    fun `no duplicate dawn info for same day`() {
        val noonMs = (daytimeMs / 86_400_000L) * 86_400_000L + 43_200_000L
        val dawnMs = SunCalculator.civilDawnMs(lat, lon, noonMs)
        val nowMs = dawnMs - 30 * 60_000L

        val first = provider.generate(nowMs, lat, lon, etaMs = null, hasRoute = true, remainingKm = 30f, isRiding = true)
        assertTrue("First call should generate dawn info", first.any { it.type == "DAWN_INFO" })

        val second = provider.generate(nowMs + 5000L, lat, lon, etaMs = null, hasRoute = true, remainingKm = 30f, isRiding = true)
        val repeat = second.firstOrNull { it.type == "DAWN_INFO" }
        assertTrue("Second call should NOT duplicate dawn info for same day", repeat == null)
    }

    @Test
    fun `warning still shown when no ETA`() {
        val duskMs = SunCalculator.civilDuskMs(lat, lon, daytimeMs)
        val nowMs = duskMs - 30 * 60_000L

        val msgs = provider.generate(nowMs, lat, lon, etaMs = null, hasRoute = true, remainingKm = 30f, isRiding = true)
        val dusk = msgs.firstOrNull { it.type == "TWILIGHT_WARNING_30" }
        assertTrue("Should show warning even without ETA", dusk != null)
    }

    @Test
    fun `resetDailyTrack allows new warnings next call`() {
        val duskMs = SunCalculator.civilDuskMs(lat, lon, daytimeMs)
        val nowMs = duskMs - 30 * 60_000L

        provider.generate(nowMs, lat, lon, etaMs = null, hasRoute = true, remainingKm = 30f, isRiding = true)
        provider.resetDailyTrack()
        val again = provider.generate(nowMs, lat, lon, etaMs = null, hasRoute = true, remainingKm = 30f, isRiding = true)
        assertTrue("After reset should generate warning again", again.any { it.type == "TWILIGHT_WARNING_30" })
    }
}
