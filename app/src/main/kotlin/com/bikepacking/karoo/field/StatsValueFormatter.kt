package com.bikepacking.karoo.field

import java.util.Locale

data class StatsFormattedValue(
    val main: String,
    val unit: String? = null
)

object StatsValueFormatter {

    fun npW(watts: Int): StatsFormattedValue {
        if (watts <= 0) return StatsFormattedValue("--")
        return StatsFormattedValue("${watts.coerceAtMost(999)}", "W")
    }

    fun ifValue(value: Float): StatsFormattedValue {
        if (value <= 0f) return StatsFormattedValue("--")
        return StatsFormattedValue(java.lang.String.format(Locale.US, "%.2f", value.coerceAtMost(1.99f)))
    }

    fun vi(value: Float): StatsFormattedValue {
        if (value <= 0f) return StatsFormattedValue("--")
        return StatsFormattedValue(java.lang.String.format(Locale.US, "%.2f", value.coerceAtMost(1.99f)))
    }

    fun carbsG(gPerH: Int): StatsFormattedValue {
        if (gPerH <= 0) return StatsFormattedValue("--")
        return StatsFormattedValue("${gPerH.coerceIn(0, 999)}", "g")
    }

    fun fluidL(lPerH: Float): StatsFormattedValue {
        if (lPerH <= 0f) return StatsFormattedValue("--")
        return StatsFormattedValue(
            java.lang.String.format(Locale.US, "%.1f", lPerH.coerceIn(0f, 9.9f)),
            "L"
        )
    }

    fun calories(kcal: Int): StatsFormattedValue {
        if (kcal <= 0) return StatsFormattedValue("--")
        return StatsFormattedValue("${kcal.coerceAtMost(99999)}")
    }

    fun tss(value: Float): StatsFormattedValue {
        if (value <= 0f) return StatsFormattedValue("--")
        return StatsFormattedValue("${value.toInt().coerceIn(0, 9999)}")
    }

    fun decouplingPct(percent: Float, hasData: Boolean): StatsFormattedValue {
        if (!hasData || percent == 0f) return StatsFormattedValue("--")
        return StatsFormattedValue(
            java.lang.String.format(Locale.US, "%+.0f", percent.coerceIn(-20f, 50f)),
            "%"
        )
    }

    fun reserveNumber(percent: Int): StatsFormattedValue {
        return StatsFormattedValue("${percent.coerceIn(-20, 100)}", "%")
    }

    fun deadlineDelta(deltaKph: Float, status: String): StatsFormattedValue {
        return when (status) {
            "OK" -> StatsFormattedValue("OK", null)
            "LATE", "IMPOSSIBLE" -> StatsFormattedValue("LATE", null)
            else -> StatsFormattedValue("--")
        }
    }

    fun deadlineDeltaValue(deltaKph: Float, status: String): StatsFormattedValue {
        return when {
            status == "OK" || deltaKph <= 0f -> StatsFormattedValue("OK", null)
            status == "LATE" || status == "IMPOSSIBLE" -> StatsFormattedValue("LATE", null)
            deltaKph > 99.9f -> StatsFormattedValue("LATE", null)
            else -> StatsFormattedValue("+${String.format(Locale.US, "%.1f", deltaKph.coerceAtMost(99.9f))}", null)
        }
    }

    fun ascentM(meters: Int): StatsFormattedValue {
        if (meters < 0) return StatsFormattedValue("--")
        return StatsFormattedValue("${meters.coerceAtMost(99999)}", "m")
    }

    fun ascentLeftM(meters: Int, hasRoute: Boolean): StatsFormattedValue {
        if (!hasRoute) return StatsFormattedValue("--")
        return StatsFormattedValue("${meters.coerceAtLeast(0).coerceAtMost(99999)}", "m")
    }

    fun etaTime(etaMs: Long): StatsFormattedValue {
        if (etaMs <= 0L) return StatsFormattedValue("--")
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = etaMs
        return StatsFormattedValue(java.lang.String.format(Locale.US, "%02d:%02d",
            cal.get(java.util.Calendar.HOUR_OF_DAY),
            cal.get(java.util.Calendar.MINUTE)))
    }

    fun avgAll(kph: Float): StatsFormattedValue {
        if (kph <= 0f) return StatsFormattedValue("--")
        return StatsFormattedValue(java.lang.String.format(Locale.US, "%.1f", kph.coerceIn(0f, 99.9f)))
    }

    fun elapsedTime(sec: Long): StatsFormattedValue {
        if (sec <= 0L) return StatsFormattedValue("--")
        return StatsFormattedValue(formatTime(sec))
    }

    fun stopTime(stoppedSec: Long): StatsFormattedValue {
        if (stoppedSec < 0L) return StatsFormattedValue("--")
        val h = stoppedSec / 3600
        val m = (stoppedSec % 3600) / 60
        return StatsFormattedValue("${h}:${m.toString().padStart(2, '0')}")
    }

    fun batteryPerHour(drop: Float): StatsFormattedValue {
        if (drop <= 0f) return StatsFormattedValue("--")
        return StatsFormattedValue("${drop.coerceIn(0f, 100f).toInt()}", "%/h")
    }

    fun batteryRuntime(sec: Long): StatsFormattedValue {
        if (sec <= 0L) return StatsFormattedValue("--")
        return StatsFormattedValue(formatTime(sec))
    }

    fun batterySimple(percent: Int): StatsFormattedValue {
        if (percent <= 0) return StatsFormattedValue("--")
        return StatsFormattedValue("${percent.coerceIn(0, 100)}", "%")
    }

    fun rdBat(percent: Int?): StatsFormattedValue {
        if (percent == null || percent <= 0) return StatsFormattedValue("--")
        return StatsFormattedValue("${percent.coerceIn(0, 100)}", "%")
    }

    fun formatTime(sec: Long): String {
        if (sec < 0L) return "--:--"
        val h = sec / 3600
        val m = (sec % 3600) / 60
        return if (h > 99) "99:59" else "${h}:${m.toString().padStart(2, '0')}"
    }
}
