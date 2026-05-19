package com.bikepacking.karoo.message

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DebugRideMessageProviderTest {

    private val nowMs = 1_000_000_000L

    @Test
    fun `OFF mode returns null`() {
        val config = DynamicMessagesConfig(debugMessageMode = DebugMessageMode.OFF)
        val prov = DebugRideMessageProvider(config)
        assertNull(prov.generate(nowMs))
    }

    @Test
    fun `INFO mode returns INFO severity`() {
        val config = DynamicMessagesConfig(debugMessageMode = DebugMessageMode.INFO)
        val prov = DebugRideMessageProvider(config)
        val msg = prov.generate(nowMs)
        assert(msg != null)
        assertEquals(MessageSeverity.INFO, msg!!.severity)
        assertEquals("DEBUG_INFO", msg.type)
    }

    @Test
    fun `WARNING mode returns WARNING severity`() {
        val config = DynamicMessagesConfig(debugMessageMode = DebugMessageMode.WARNING)
        val prov = DebugRideMessageProvider(config)
        val msg = prov.generate(nowMs)
        assert(msg != null)
        assertEquals(MessageSeverity.WARNING, msg!!.severity)
        assertEquals("DEBUG_WARNING", msg.type)
    }

    @Test
    fun `ALARM mode returns ALARM severity`() {
        val config = DynamicMessagesConfig(debugMessageMode = DebugMessageMode.ALARM)
        val prov = DebugRideMessageProvider(config)
        val msg = prov.generate(nowMs)
        assert(msg != null)
        assertEquals(MessageSeverity.ALARM, msg!!.severity)
        assertEquals("DEBUG_ALARM", msg.type)
    }

    @Test
    fun `CLIMB_AHEAD debug line1 and severity correct`() {
        val config = DynamicMessagesConfig(debugMessageMode = DebugMessageMode.CLIMB_AHEAD)
        val prov = DebugRideMessageProvider(config)
        val msg = prov.generate(nowMs)
        assert(msg != null)
        assertEquals("CLIMB_AHEAD", msg!!.type)
        assertEquals(MessageSeverity.WARNING, msg.severity)
        assertEquals("PODJAZD 3/7", msg.line1)
        assertEquals("850m · 6%", msg.line2)
    }

    @Test
    fun `CLIMB_TARGET debug line1 and severity correct`() {
        val config = DynamicMessagesConfig(debugMessageMode = DebugMessageMode.CLIMB_TARGET)
        val prov = DebugRideMessageProvider(config)
        val msg = prov.generate(nowMs)
        assert(msg != null)
        assertEquals("CLIMB_HOLD_TARGET", msg!!.type)
        assertEquals(MessageSeverity.INFO, msg.severity)
        assertEquals("3/7 · 7%", msg.line1)
        assertEquals("TRZYMAJ 190–210W", msg.line2)
    }

    @Test
    fun `EASE_OFF debug line1 and severity correct`() {
        val config = DynamicMessagesConfig(debugMessageMode = DebugMessageMode.EASE_OFF)
        val prov = DebugRideMessageProvider(config)
        val msg = prov.generate(nowMs)
        assert(msg != null)
        assertEquals("CLIMB_EASE_OFF", msg!!.type)
        assertEquals(MessageSeverity.ALARM, msg.severity)
        assertEquals("ODPUŚĆ!", msg.line1)
        assertEquals("CEL 190–210W", msg.line2)
    }

    @Test
    fun `SUMMARY debug line1 and severity correct`() {
        val config = DynamicMessagesConfig(debugMessageMode = DebugMessageMode.SUMMARY)
        val prov = DebugRideMessageProvider(config)
        val msg = prov.generate(nowMs)
        assert(msg != null)
        assertEquals("CLIMB_SUMMARY", msg!!.type)
        assertEquals(MessageSeverity.WARNING, msg.severity)
        assertEquals("3/7 ZA MOCNO", msg.line1)
        assertEquals("+54m · zostało 420m", msg.line2)
    }
}
