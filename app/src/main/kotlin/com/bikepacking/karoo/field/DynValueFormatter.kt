package com.bikepacking.karoo.field

import android.graphics.Color
import java.util.Locale

/**
 * Pure functions for DYN 4x2 field formatting.
 * No Android dependencies except Color constants.
 * Testable without instrumentation.
 */
object DynValueFormatter {

    data class Formatted(
        val value: String,
        val colorHex: String = "#F8FAFC",
        val bgHex: String? = null,
    )

    // ── D: Distance Done (km) ──
    fun distanceDone(distKm: Float): Formatted {
        if (distKm <= 0f) return Formatted("--", bgHex = "#111827")
        return Formatted(
            value = String.format(Locale.US, "%.1f", distKm.coerceAtMost(9999f)),
            bgHex = "#111827",
        )
    }

    // ── IF10: Intensity Factor last 10 min ──
    fun if10(if10Value: Float): Formatted {
        if (if10Value <= 0f) return Formatted(".--")
        return Formatted(
            value = String.format(Locale.US, "%.2f", if10Value.coerceAtMost(1.99f)),
            colorHex = if10Color(if10Value),
        )
    }

    // ── NP10: Normalized Power last 10 min ──
    fun np10(npWatts: Int): Formatted {
        if (npWatts <= 0) return Formatted("--")
        return Formatted(
            value = npWatts.coerceAtMost(999).toString(),
        )
    }

    // ── W': W' Balance % ──
    fun wbal(percent: Int): Formatted {
        if (percent < 0) return Formatted("--%")
        return Formatted(
            value = "${percent.coerceAtMost(100)}%",
            colorHex = wbalColor(percent),
        )
    }

    // ── DTD: Distance To Destination (km) ──
    fun distanceToDest(remainingKm: Float, hasRoute: Boolean): Formatted {
        if (!hasRoute || remainingKm <= 0f) return Formatted("--")
        return Formatted(
            value = String.format(Locale.US, "%.1f", remainingKm.coerceAtMost(9999f)),
        )
    }

    // ── Vśr: Smart Average Net Speed (kph) ──
    fun avgNetSpeed(kph: Float): Formatted {
        if (kph <= 0f) return Formatted("00.0")
        return Formatted(
            value = String.format(Locale.US, "%.1f", kph.coerceAtMost(120f)),
        )
    }

    // ── T: Temperature (°C) ──
    fun temperature(celsius: Float?): Formatted {
        if (celsius == null) return Formatted("--°")
        return Formatted(
            value = "${celsius.toInt()}°",
            colorHex = tmpColor(celsius),
        )
    }

    // ── W: Wind (arrow + m/s) ──
    fun wind(arrow: String, speedMs: Float, impactKph: Float): Formatted {
        if (speedMs <= 0f) return Formatted("- 0.0")
        return Formatted(
            value = "$arrow ${String.format(Locale.US, "%.1f", speedMs)}",
            bgHex = windBgColor(impactKph),
        )
    }

    // ── W: Wind with headwind error code ──
    fun windWithError(arrow: String, speedMs: Float, impactKph: Float, headwindError: Int): Formatted {
        when (headwindError) {
            -1 -> return Formatted("– --", bgHex = "#111827") // no GPS
            -2 -> return Formatted("– --", bgHex = "#111827") // no weather
            -3 -> return Formatted("– --", bgHex = "#111827") // not configured
        }
        // headwindError == 0: normal formatting
        return wind(arrow, speedMs, impactKph)
    }

    // ── Color helpers ──

    fun if10Color(if10: Float): String = when {
        if10 <= 0f -> "#F8FAFC"
        if10 < 0.65f -> "#22C55E"
        if10 < 0.85f -> "#F59E0B"
        else -> "#EF4444"
    }

    fun tmpColor(tmp: Float): String = when {
        tmp < 5f -> "#60A5FA"
        tmp < 20f -> "#F8FAFC"
        tmp < 30f -> "#FCD34D"
        else -> "#FB923C"
    }

    fun wbalColor(wbal: Int): String = when {
        wbal < 0 -> "#F8FAFC"
        wbal >= 40 -> "#4ADE80"
        wbal >= 20 -> "#FCD34D"
        else -> "#EF4444"
    }

    fun windBgColor(impactKph: Float): String = when {
        impactKph < -1f -> "#EF4444"
        impactKph > 1f  -> "#22C55E"
        else            -> "#111827"
    }

    fun wbalBgColor(percent: Int): String = when {
        percent < 0  -> "#111827"
        percent >= 60 -> "#111827"
        percent >= 30 -> "#B45309"
        else         -> "#991B1B"
    }

    // ── Color to Android int (for RemoteViews) ──
    fun colorInt(hex: String): Int = Color.parseColor(hex)
}
