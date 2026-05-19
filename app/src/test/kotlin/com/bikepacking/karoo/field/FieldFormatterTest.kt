package com.bikepacking.karoo.field

import org.junit.Assert.assertEquals
import org.junit.Test

class FieldFormatterTest {

    @Test
    fun `speed formats positive`() {
        assertEquals("24.5", FieldFormatter.speed(24.5f))
    }

    @Test
    fun `speed formats zero`() {
        assertEquals("0.0", FieldFormatter.speed(0f))
    }

    @Test
    fun `speed formats negative as zero`() {
        assertEquals("0.0", FieldFormatter.speed(-1f))
    }

    @Test
    fun `speed rounds above 100`() {
        assertEquals("105", FieldFormatter.speed(105.2f))
    }

    @Test
    fun `speed truncates at 100 exactly`() {
        assertEquals("100", FieldFormatter.speed(100.0f))
    }

    @Test
    fun `power shows fallback when zero`() {
        assertEquals("--", FieldFormatter.power(0))
    }

    @Test
    fun `power shows fallback when negative`() {
        assertEquals("--", FieldFormatter.power(-5))
    }

    @Test
    fun `power formats normal`() {
        assertEquals("186", FieldFormatter.power(186))
    }

    @Test
    fun `power caps at 999`() {
        assertEquals("999", FieldFormatter.power(1200))
    }

    @Test
    fun `hr shows fallback when zero`() {
        assertEquals("--", FieldFormatter.hr(0))
    }

    @Test
    fun `hr formats normal`() {
        assertEquals("142", FieldFormatter.hr(142))
    }

    @Test
    fun `hr caps at 220`() {
        assertEquals("220", FieldFormatter.hr(250))
    }

    @Test
    fun `cadence shows fallback when zero`() {
        assertEquals("--", FieldFormatter.cadence(0))
    }

    @Test
    fun `cadence formats normal`() {
        assertEquals("67", FieldFormatter.cadence(67))
    }

    @Test
    fun `cadence caps at 99`() {
        assertEquals("99", FieldFormatter.cadence(120))
    }

    @Test
    fun `grade shows positive with sign`() {
        assertEquals("+4.5", FieldFormatter.grade(4.5f))
    }

    @Test
    fun `grade shows negative with sign`() {
        assertEquals("-3.2", FieldFormatter.grade(-3.2f))
    }

    @Test
    fun `grade shows zero as flat`() {
        // 0% is valid flat road, should show "+0.0"
        assertEquals("+0.0", FieldFormatter.grade(0f))
    }

    @Test
    fun `grade handles large value`() {
        assertEquals("+150", FieldFormatter.grade(150f))
    }

    @Test
    fun `grade handles large negative`() {
        assertEquals("-200", FieldFormatter.grade(-200f))
    }

    @Test
    fun `grade caps at 999`() {
        assertEquals("-999", FieldFormatter.grade(-1500f))
    }

    @Test
    fun `gear shows fallback when no data`() {
        assertEquals("--", FieldFormatter.gear(0, 0))
    }

    @Test
    fun `gear shows fallback when front missing`() {
        assertEquals("--", FieldFormatter.gear(0, 15))
    }

    @Test
    fun `gear shows ratio`() {
        assertEquals("40×15", FieldFormatter.gear(40, 15))
    }

    @Test
    fun `gear clamps values`() {
        assertEquals("99×99", FieldFormatter.gear(100, 150))
    }
}
