package com.bikepacking.karoo

import com.bikepacking.karoo.message.RideMessage

data class RideState(
    val speedKph: Float = 0f,
    val powerWatts: Int = 0,
    val heartRate: Int = 0,
    val cadenceRpm: Int = 0,
    val gradePercent: Float = 0f,
    val distanceKm: Float = 0f,
    val remainingKm: Float = 0f,
    val elapsedSec: Long = 0L,
    val movingSec: Long = 0L,
    val hasRoute: Boolean = false,
    val np10Watts: Int = 0,
    val if10Value: Float = 0f,
    val ifValue: Float = 0f,
    val cadenceAvg30sRpm: Int = 0,
    val smartAvgKph: Float = 0f,
    val smartAvgNetKph: Float = 0f,
    val speedTrend: SpeedTrend = SpeedTrend.NEUTRAL,
    val etaTimestamp: Long = 0L,
    val requiredSpeedKph: Float = 0f,
    val deadlineTimestamp: Long = 0L,
    val deadlineDeltaKph: Float = 0f,  // positive = need faster, negative/zero = OK
    val deadlineStatus: String = "--", // OK/LATE/IMPOSSIBLE
    val civilTwilightTimestamp: Long = 0L,
    val stopRateMinPerKm: Float = 0f,
    val isOverDeadline: Boolean = false,
    val isAfterTwilight: Boolean = false,
    val minutesOverDeadline: Int = 0,
    val windSpeedMs: Float = 0f,
    val windDirectionDeg: Int = 0,
    val windArrow: String = "–",
    val windImpactKph: Float = 0f,
    val headwindError: Int = 0, // 0=OK, -1=no GPS, -2=no weather, -3=not configured
    val lastLat: Double = 0.0,
    val lastLon: Double = 0.0,
    val temperatureCelsius: Float? = null,
    val frontTeeth: Int = 0,
    val rearTeeth: Int = 0,
    // BP STATS
    val npWholeWatts: Int = 0,
    val ifWholeRide: Float = 0f,
    val viValue: Float = 0f,
    val tssValue: Float = 0f,
    val caloriesKcal: Int = 0,
    val avgPowerWatts: Int = 0,
    val ascentDoneM: Int = 0,
    val ascentLeftM: Int = 0,
    val timeToFinishSec: Long = 0L,
    val decouplingPercent: Float = 0f,
    val carbsGPerH: Int = 0,
    val fluidLPerH: Float = 0f,
    val todayFactor: Float = 1.0f,
    val batteryDropPerHour: Float = 0f,
    val batteryRuntimeSec: Long = 0L,
    val rideReservePercent: Int = 0,
    val batteryPercent: Int = 0,
    val rearDerailleurBatteryPercent: Int? = null,
    // HRD: Local HR Drift
    val hrdStatus: String = "WAIT",
    val hrdPct: Float = 0f,
    val hrdPhase: String = "WAIT",
    val hrdValid: Boolean = false,
    val hrdReason: String = "",
    // W' Balance
    val wBalancePercent: Int = -1,
    // Active ride message
    val activeRideMessage: RideMessage? = null,
)

enum class SpeedTrend { INCREASING, NEUTRAL, DECREASING }

enum class PowerZone(val label: String, val colorHex: String) {
    Z1("Z1", "#3b3b3b"), Z2("Z2", "#185fa5"), Z3("Z3", "#4a7c2a"),
    Z4("Z4", "#8a6800"), Z5("Z5", "#8a3800"), Z6("Z6", "#a32d2d"),
    Z7("Z7", "#5a1d7a"), UNKNOWN("--", "#232c45");
    companion object {
        fun fromPower(watts: Int, ftp: Int): PowerZone {
            if (ftp <= 0 || watts <= 0) return UNKNOWN
            return when {
                watts < ftp * 0.55 -> Z1; watts < ftp * 0.75 -> Z2
                watts < ftp * 0.87 -> Z3; watts < ftp * 1.00 -> Z4
                watts < ftp * 1.15 -> Z5; watts < ftp * 1.50 -> Z6
                else -> Z7
            }
        }
    }
}

enum class HrZone(val label: String, val colorHex: String) {
    Z1("Z1", "#3b3b3b"), Z2("Z2", "#185fa5"), Z3("Z3", "#4a7c2a"),
    Z4("Z4", "#8a6800"), Z5("Z5", "#a32d2d"), UNKNOWN("--", "#232c45");
    companion object {
        fun fromHr(bpm: Int, maxHr: Int): HrZone {
            if (maxHr <= 0 || bpm <= 0) return UNKNOWN
            return when {
                bpm < maxHr * 0.60 -> Z1; bpm < maxHr * 0.75 -> Z2
                bpm < maxHr * 0.85 -> Z3; bpm < maxHr * 0.95 -> Z4
                else -> Z5
            }
        }
    }
}