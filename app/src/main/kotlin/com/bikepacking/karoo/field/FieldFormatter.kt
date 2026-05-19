package com.bikepacking.karoo.field

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

object FieldFormatter {

    fun speed(kph: Float): String {
        if (kph < 0f) return "0.0"
        return if (kph >= 100f) java.lang.String.format(Locale.US, "%d", kph.roundToInt())
        else java.lang.String.format(Locale.US, "%.1f", kph)
    }

    fun power(watts: Int): String {
        if (watts <= 0) return "--"
        return if (watts > 999) "999" else java.lang.String.format(Locale.US, "%d", watts)
    }

    fun hr(bpm: Int): String {
        if (bpm <= 0) return "--"
        return if (bpm > 220) "220" else java.lang.String.format(Locale.US, "%d", bpm)
    }

    fun cadence(rpm: Int): String {
        if (rpm <= 0) return "--"
        return if (rpm > 99) "99" else java.lang.String.format(Locale.US, "%d", rpm)
    }

    fun grade(percent: Float): String {
        // 0% is valid flat road - show "+0.0", not "--"
        // "--" should only come from freshness MISSING/STALE in UI, not from value
        if (percent == 0f) return "+0.0"
        val sign = if (percent >= 0f) "+" else "-"
        val absVal = abs(percent)
        val display = if (absVal >= 100f) java.lang.String.format(Locale.US, "%d", absVal.roundToInt().coerceAtMost(999))
        else java.lang.String.format(Locale.US, "%.1f", absVal)
        return "$sign$display"
    }

    fun gear(front: Int, rear: Int): String {
        if (front <= 0 || rear <= 0) return "--"
        val f = front.coerceIn(0, 99)
        val r = rear.coerceIn(0, 99)
        return java.lang.String.format(Locale.US, "%d×%d", f, r)
    }
}
