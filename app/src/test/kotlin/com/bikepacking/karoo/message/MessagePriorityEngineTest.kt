package com.bikepacking.karoo.message

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MessagePriorityEngineTest {

    private val infoMsg = RideMessage(
        type = "test_info", module = RideMessageModule.CLIMB_PACING,
        severity = MessageSeverity.INFO, priority = 0,
        line1 = "Info", minDisplayMs = 100L, cooldownMs = 0L
    )
    private val warnMsg = infoMsg.copy(type = "test_warn", severity = MessageSeverity.WARNING, line1 = "Warn")
    private val alarmMsg = infoMsg.copy(type = "test_alarm", severity = MessageSeverity.ALARM, line1 = "Alarm")
    private val criticalMsg = infoMsg.copy(type = "test_critical", severity = MessageSeverity.CRITICAL, line1 = "Critical")

    private fun engine(config: DynamicMessagesConfig = DynamicMessagesConfig()) = MessagePriorityEngine(config)

    @Test
    fun `higher severity wins`() {
        val eng = engine()
        val result = eng.select(listOf(infoMsg, alarmMsg), nowMs = 0L)
        assertNotNull(result)
        assertEquals(MessageSeverity.ALARM, result!!.severity)
    }

    @Test
    fun `disabled module does not generate active message`() {
        val config = DynamicMessagesConfig(moduleEnabled = mapOf(RideMessageModule.CLIMB_PACING to false))
        val eng = engine(config)
        val result = eng.select(listOf(infoMsg), nowMs = 0L)
        assertNull(result)
    }

    @Test
    fun `global off blocks all messages`() {
        val config = DynamicMessagesConfig(enabled = false)
        val eng = engine(config)
        val result = eng.select(listOf(alarmMsg), nowMs = 0L)
        assertNull(result)
    }

    @Test
    fun `informational off blocks INFO`() {
        val config = DynamicMessagesConfig(informationalMessagesEnabled = false)
        val eng = engine(config)
        val result = eng.select(listOf(infoMsg), nowMs = 0L)
        assertNull(result)
    }

    @Test
    fun `informational off blocks WARNING`() {
        val config = DynamicMessagesConfig(informationalMessagesEnabled = false)
        val eng = engine(config)
        val result = eng.select(listOf(warnMsg), nowMs = 0L)
        assertNull(result)
    }

    @Test
    fun `informational off does not block ALARM`() {
        val config = DynamicMessagesConfig(informationalMessagesEnabled = false)
        val eng = engine(config)
        val result = eng.select(listOf(alarmMsg), nowMs = 0L)
        assertNotNull(result)
    }

    @Test
    fun `alarm off blocks ALARM`() {
        val config = DynamicMessagesConfig(alarmMessagesEnabled = false)
        val eng = engine(config)
        val result = eng.select(listOf(alarmMsg), nowMs = 0L)
        assertNull(result)
    }

    @Test
    fun `alarm off blocks CRITICAL`() {
        val config = DynamicMessagesConfig(alarmMessagesEnabled = false)
        val eng = engine(config)
        val result = eng.select(listOf(criticalMsg), nowMs = 0L)
        assertNull(result)
    }

    @Test
    fun `cooldown blocks repeat`() {
        val msg = infoMsg.copy(type = "repeat", cooldownMs = 500L)
        val eng = engine()
        val first = eng.select(listOf(msg), nowMs = 0L)
        assertNotNull(first)

        val second = eng.select(listOf(msg), nowMs = 200L)
        assertNull(second)
    }

    @Test
    fun `cooldown expires allows repeat`() {
        val msg = infoMsg.copy(type = "repeat_expire", cooldownMs = 500L)
        val eng = engine()
        eng.select(listOf(msg), nowMs = 0L)
        val second = eng.select(listOf(msg), nowMs = 600L)
        assertNotNull(second)
    }

    @Test
    fun `minDisplayMs keeps active message`() {
        val msg = infoMsg.copy(type = "display", minDisplayMs = 500L)
        val eng = engine()
        eng.select(listOf(msg), nowMs = 0L)
        val during = eng.select(listOf(criticalMsg), nowMs = 200L)
        assertNotNull(during)
        assertEquals("display", during!!.type)
    }

    @Test
    fun `after minDisplayMs active message can be replaced`() {
        val msg = infoMsg.copy(type = "replace_me", minDisplayMs = 300L)
        val eng = engine()
        eng.select(listOf(msg), nowMs = 0L)
        val after = eng.select(listOf(criticalMsg), nowMs = 500L)
        assertNotNull(after)
        assertEquals(MessageSeverity.CRITICAL, after!!.severity)
    }

    @Test
    fun `no candidates returns null`() {
        val eng = engine()
        val result = eng.select(emptyList(), nowMs = 0L)
        assertNull(result)
    }

    @Test
    fun `dismissCurrent clears active message`() {
        val msg = infoMsg.copy(type = "dismiss_test", minDisplayMs = 10_000L)
        val eng = engine()
        eng.select(listOf(msg), nowMs = 0L)
        assertEquals(msg.type, eng.currentActiveMessage()!!.type)
        eng.dismissCurrent()
        assertNull(eng.currentActiveMessage())
    }

    @Test
    fun `messageKey includes module and type`() {
        val msg = infoMsg.copy(module = RideMessageModule.INTAKE, type = "drink")
        val key = MessagePriorityEngine.messageKey(msg)
        assertEquals("INTAKE_drink", key)
    }

    @Test
    fun `higher priority picks ahead when same severity`() {
        val lowPri = infoMsg.copy(type = "low", priority = 0)
        val highPri = infoMsg.copy(type = "high", priority = 10)
        val eng = engine()
        val result = eng.select(listOf(lowPri, highPri), nowMs = 0L)
        assertNotNull(result)
        assertEquals("high", result!!.type)
    }
}
