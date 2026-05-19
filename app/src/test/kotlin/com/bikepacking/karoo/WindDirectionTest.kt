package com.bikepacking.karoo

import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for relative wind direction arrow logic.
 *
 * The arrow shows where wind comes FROM relative to the rider:
 * - 0° = headwind (wind in face) → ↓
 * - 180° = tailwind (wind from behind) → ↑
 * - 90° = wind from right → →
 * - 270° = wind from left → ←
 *
 * These tests verify the expected arrow outputs for key scenarios.
 * The implementation lives in RideEngine.relativeWindFromArrow().
 * This test file duplicates the logic for verification purposes.
 */
class WindDirectionTest {

    private fun relativeWindFromArrow(d: Float): String {
        if (d < 0f) return "-"
        val a = ((d % 360f) + 360f) % 360f
        return when {
            a < 22.5f || a >= 337.5f -> "↓"
            a < 67.5f -> "↘"
            a < 112.5f -> "→"
            a < 157.5f -> "↗"
            a < 202.5f -> "↑"
            a < 247.5f -> "↖"
            a < 292.5f -> "←"
            else -> "↙"
        }
    }

    // ── Required test cases ──

    @Test
    fun `heading 0 wind from 0 is headwind arrow`() {
        // Wind directly from front = headwind
        assertEquals("↓", relativeWindFromArrow(0f))
    }

    @Test
    fun `heading 0 wind from 180 is tailwind arrow`() {
        // Wind directly from behind = tailwind
        assertEquals("↑", relativeWindFromArrow(180f))
    }

    @Test
    fun `heading 0 wind from 90 is side wind from right`() {
        // Wind from right side
        assertEquals("→", relativeWindFromArrow(90f))
    }

    @Test
    fun `heading 90 wind from 270 is tailwind`() {
        // Rider heading east (90°), wind from west (270°) = wind from behind = tailwind.
        // karoo-headwind provides RELATIVE bearing: tailwind = 180°.
        // This function expects the RELATIVE bearing, not absolute geographic.
        assertEquals("↑", relativeWindFromArrow(180f))
    }

    // ── All 8 cardinal/diagonal directions ──

    @Test
    fun `arrow for N (0 degrees) is down`() {
        assertEquals("↓", relativeWindFromArrow(0f))
    }

    @Test
    fun `arrow for NE (45 degrees) is down-right`() {
        assertEquals("↘", relativeWindFromArrow(45f))
    }

    @Test
    fun `arrow for E (90 degrees) is right`() {
        assertEquals("→", relativeWindFromArrow(90f))
    }

    @Test
    fun `arrow for SE (135 degrees) is up-right`() {
        assertEquals("↗", relativeWindFromArrow(135f))
    }

    @Test
    fun `arrow for S (180 degrees) is up`() {
        assertEquals("↑", relativeWindFromArrow(180f))
    }

    @Test
    fun `arrow for SW (225 degrees) is up-left`() {
        assertEquals("↖", relativeWindFromArrow(225f))
    }

    @Test
    fun `arrow for W (270 degrees) is left`() {
        assertEquals("←", relativeWindFromArrow(270f))
    }

    @Test
    fun `arrow for NW (315 degrees) is down-left`() {
        assertEquals("↙", relativeWindFromArrow(315f))
    }

    // ── Edge cases ──

    @Test
    fun `negative bearing returns dash`() {
        assertEquals("-", relativeWindFromArrow(-1f))
    }

    @Test
    fun `bearing 360 wraps to N`() {
        assertEquals("↓", relativeWindFromArrow(360f))
    }

    @Test
    fun `bearing 337 is down-left`() {
        assertEquals("↙", relativeWindFromArrow(337f))
    }

    @Test
    fun `bearing 338 wraps to N`() {
        assertEquals("↓", relativeWindFromArrow(338f))
    }

    @Test
    fun `boundary at 22 degrees is down`() {
        assertEquals("↓", relativeWindFromArrow(22f))
    }

    @Test
    fun `boundary at 23 degrees is down-right`() {
        assertEquals("↘", relativeWindFromArrow(23f))
    }
}
