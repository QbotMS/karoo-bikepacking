package com.bikepacking.karoo.field

import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class BpLive3x2ColorTest {

    @Test
    fun `live3x2Color returns TRANSPARENT for NEUTRAL`() {
        assertEquals(Color.TRANSPARENT, BpLive3x2DataType.live3x2Color(FieldState.NEUTRAL))
    }

    @Test
    fun `live3x2Color returns TRANSPARENT for OK`() {
        assertEquals(Color.TRANSPARENT, BpLive3x2DataType.live3x2Color(FieldState.OK))
    }

    @Test
    fun `live3x2Color returns original green for GOOD`() {
        assertEquals((0xFF0A2E1A).toInt(), BpLive3x2DataType.live3x2Color(FieldState.GOOD))
    }

    @Test
    fun `live3x2Color returns dark red for WARN`() {
        assertEquals((0xFF7F1D1D).toInt(), BpLive3x2DataType.live3x2Color(FieldState.WARN))
    }

    @Test
    fun `live3x2Color returns red for BAD`() {
        assertEquals((0xFFB91C1C).toInt(), BpLive3x2DataType.live3x2Color(FieldState.BAD))
    }

    @Test
    fun `genericValueTextColorForState returns green for GOOD`() {
        assertEquals((0xFF22C55E).toInt(), BpLive3x2DataType.genericValueTextColorForState(FieldState.GOOD))
    }

    @Test
    fun `genericValueTextColorForState returns WHITE for OK`() {
        assertEquals(Color.WHITE, BpLive3x2DataType.genericValueTextColorForState(FieldState.OK))
    }

    @Test
    fun `genericValueTextColorForState returns WHITE for NEUTRAL`() {
        assertEquals(Color.WHITE, BpLive3x2DataType.genericValueTextColorForState(FieldState.NEUTRAL))
    }

    @Test
    fun `genericValueTextColorForState returns orange for WARN`() {
        assertEquals((0xFFF97316).toInt(), BpLive3x2DataType.genericValueTextColorForState(FieldState.WARN))
    }

    @Test
    fun `genericValueTextColorForState returns red for BAD`() {
        assertEquals((0xFFEF4444).toInt(), BpLive3x2DataType.genericValueTextColorForState(FieldState.BAD))
    }
}
