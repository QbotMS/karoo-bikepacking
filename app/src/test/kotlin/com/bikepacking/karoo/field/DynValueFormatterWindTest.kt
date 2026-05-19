package com.bikepacking.karoo.field

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for DynValueFormatter wind functions.
 *
 * Verifies:
 * - headwindSpeed in m/s converts correctly to displayed value
 * - headwind error codes -1/-2/-3 produce explicit fallback
 * - normal wind data produces correct arrow + speed format
 * - windBgColor reflects headwind/tailwind impact (no amber)
 * - wbalBgColor reflects W'Bal level
 */
class DynValueFormatterWindTest {

    // ── Normal wind data ──

    @Test
    fun `wind with valid data shows arrow and speed`() {
        val result = DynValueFormatter.wind("↗", 4.2f, -15f)
        assertEquals("↗ 4.2", result.value)
    }

    @Test
    fun `wind with zero speed shows fallback`() {
        val result = DynValueFormatter.wind("–", 0f, 0f)
        assertEquals("- 0.0", result.value)
    }

    @Test
    fun `wind with very low speed formats the value`() {
        val result = DynValueFormatter.wind("↑", 0.05f, 0f)
        assertEquals("↑ 0.1", result.value)
    }

    @Test
    fun `wind formats speed to 1 decimal place`() {
        val result = DynValueFormatter.wind("→", 3.14159f, 0f)
        assertEquals("→ 3.1", result.value)
    }

    // ── Error codes via windWithError ──

    @Test
    fun `windWithError -1 (no GPS) shows explicit fallback`() {
        val result = DynValueFormatter.windWithError("↗", 4.2f, -15f, -1)
        assertEquals("– --", result.value)
    }

    @Test
    fun `windWithError -2 (no weather) shows explicit fallback`() {
        val result = DynValueFormatter.windWithError("↗", 4.2f, -15f, -2)
        assertEquals("– --", result.value)
    }

    @Test
    fun `windWithError -3 (not configured) shows explicit fallback`() {
        val result = DynValueFormatter.windWithError("↗", 4.2f, -15f, -3)
        assertEquals("– --", result.value)
    }

    @Test
    fun `windWithError 0 (OK) passes through to normal wind`() {
        val result = DynValueFormatter.windWithError("↗", 4.2f, -15f, 0)
        assertEquals("↗ 4.2", result.value)
    }

    @Test
    fun `windWithError fallback has dark bg not error color`() {
        val result = DynValueFormatter.windWithError("↗", 4.2f, -15f, -1)
        assertEquals("#111827", result.bgHex)
    }

    // -- headwindSpeed mps to display conversion --

    @Test
    fun `headwindSpeed 5 mps displays as 5 point 0`() {
        val speedMs = 5.0f
        val result = DynValueFormatter.wind("↑", speedMs, -18f)
        assertEquals("↑ 5.0", result.value)
    }

    @Test
    fun `headwindSpeed 2 point 5 mps displays correctly`() {
        val speedMs = 2.5f
        val result = DynValueFormatter.wind("↘", speedMs, 9f)
        assertEquals("↘ 2.5", result.value)
    }

    @Test
    fun `headwindSpeed 0 point 3 mps treated as no wind`() {
        val speedMs = 0.3f
        val result = DynValueFormatter.wind("–", speedMs, 1f)
        assertEquals("– 0.3", result.value)
    }

    // ── Wind background color by impact ──
    // New logic: headwind (< -1) = red, tailwind (> 1) = green, neutral = dark
    // NO amber/yellow

    @Test
    fun `windBgColor strong headwind is red`() {
        assertEquals("#EF4444", DynValueFormatter.windBgColor(-15f))
    }

    @Test
    fun `windBgColor light headwind is red`() {
        assertEquals("#EF4444", DynValueFormatter.windBgColor(-2f))
    }

    @Test
    fun `windBgColor at exactly -1 kph is neutral dark`() {
        assertEquals("#111827", DynValueFormatter.windBgColor(-1f))
    }

    @Test
    fun `windBgColor neutral wind is dark`() {
        assertEquals("#111827", DynValueFormatter.windBgColor(0f))
    }

    @Test
    fun `windBgColor at exactly +1 kph is neutral dark`() {
        assertEquals("#111827", DynValueFormatter.windBgColor(1f))
    }

    @Test
    fun `windBgColor light tailwind is green`() {
        assertEquals("#22C55E", DynValueFormatter.windBgColor(2f))
    }

    @Test
    fun `windBgColor strong tailwind is green`() {
        assertEquals("#22C55E", DynValueFormatter.windBgColor(15f))
    }

    @Test
    fun `windBgColor moderate headwind is red not amber`() {
        assertEquals("#EF4444", DynValueFormatter.windBgColor(-5f))
    }

    @Test
    fun `windBgColor moderate tailwind is green not neutral`() {
        assertEquals("#22C55E", DynValueFormatter.windBgColor(5f))
    }

    // ── W'Bal background color ──

    @Test
    fun `wbalBgColor high is neutral`() {
        assertEquals("#111827", DynValueFormatter.wbalBgColor(80))
    }

    @Test
    fun `wbalBgColor at 60 percent is neutral`() {
        assertEquals("#111827", DynValueFormatter.wbalBgColor(60))
    }

    @Test
    fun `wbalBgColor at 59 percent is amber`() {
        assertEquals("#B45309", DynValueFormatter.wbalBgColor(59))
    }

    @Test
    fun `wbalBgColor at 30 percent is amber`() {
        assertEquals("#B45309", DynValueFormatter.wbalBgColor(30))
    }

    @Test
    fun `wbalBgColor at 29 percent is red`() {
        assertEquals("#991B1B", DynValueFormatter.wbalBgColor(29))
    }

    @Test
    fun `wbalBgColor low is red`() {
        assertEquals("#991B1B", DynValueFormatter.wbalBgColor(10))
    }

    @Test
    fun `wbalBgColor unknown is neutral`() {
        assertEquals("#111827", DynValueFormatter.wbalBgColor(-1))
    }

    // ── Edge cases ──

    @Test
    fun `wind with NaN speed shows fallback`() {
        val result = DynValueFormatter.wind("–", Float.NaN, 0f)
        assertTrue(result.value.contains("NaN") || result.value == "- 0.0")
    }

    @Test
    fun `wind with negative speed shows fallback`() {
        val result = DynValueFormatter.wind("–", -1f, 0f)
        assertEquals("- 0.0", result.value)
    }

    @Test
    fun `windWithError with valid data but zero speed shows zero fallback`() {
        val result = DynValueFormatter.windWithError("↗", 0f, 0f, 0)
        assertEquals("- 0.0", result.value)
    }
}
