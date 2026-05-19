package com.bikepacking.karoo.field

data class TodayProfile(
    val todayFactor: Float,
) {
    fun adjustedFtp(baseFtp: Int): Int {
        if (baseFtp <= 0) return baseFtp
        return (baseFtp * todayFactor).roundToInt().coerceAtLeast(50)
    }

    fun freshnessDescription(): String = when {
        todayFactor >= 0.95f -> "fresh"
        todayFactor >= 0.85f -> "fair"
        todayFactor >= 0.75f -> "tired"
        else -> "fatigued"
    }

    companion object {
        fun from(todayFactor: Float): TodayProfile = TodayProfile(
            todayFactor = todayFactor.coerceIn(0.5f, 1.1f),
        )
    }
}

internal fun Float.roundToInt(): Int = (this + 0.5f).toInt()
