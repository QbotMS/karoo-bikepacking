package com.bikepacking.karoo.fitexport

/**
 * ReplayTick — raw sensor sample from FIT file.
 *
 * Every field is nullable per QLab contract:
 *   "brak danych => null, nie zgadywać wartości."
 *
 * Source of truth for the shape: QLab ReplayTick adapter
 * in ~/Projects/QLab/web/lab.js
 */
data class ReplayTick(
    val timestampMs: Long,
    val distanceM: Double?,
    val speedMps: Double?,
    val powerW: Int?,
    val heartRateBpm: Int?,
    val cadenceRpm: Int?,
    val gradePct: Double?,
    val altitudeM: Double?,
    val temperatureC: Double?,
    val gear: GearInfo?,
    val route: RouteInfo?,
    val position: PositionInfo?,
    val wind: WindInfo?,
    // Pre-computed HUD snapshot (present only in output log)
    val hudState: HudStateSnapshot? = null,
)

data class GearInfo(
    val frontTeeth: Int?,
    val rearTeeth: Int?,
    val sourceTrusted: Boolean = false,
)

data class RouteInfo(
    val distanceToDestinationM: Double?,
    val ascentRemainingM: Double?,
    val activeClimbIndex: Int?,
    val climbsTotal: Int?,
)

data class PositionInfo(
    val lat: Double?,
    val lon: Double?,
)

data class WindInfo(
    val speedMs: Double?,
    val directionDeg: Int?,
    val impactKph: Double?,
)

/**
 * HudStateSnapshot — per-tick HUD decisions from RideEngine.
 *
 * Source of truth: QLab HudStateSnapshot in ~/Projects/QLab/web/lab.js
 */
data class HudStateSnapshot(
    val tick: Int,
    val fields: HudFields,
    val dyn: DynSnapshot,
    val stats: Map<String, Any?>,
    val message: RideMessageSnapshot?,
)

data class HudFields(
    val speed: FieldSnapshot,
    val power: PowerFieldSnapshot,
    val hr: FieldSnapshot,
    val cadence: FieldSnapshot,
    val grade: FieldSnapshot,
    val gear: FieldSnapshot,
)

data class FieldSnapshot(
    val state: String,     // good | ok | warn | bad | neutral
    val value: String,     // formatted display value
    val reason: String,    // classifier reason code
)

data class PowerFieldSnapshot(
    val state: String,
    val value: String,
    val reason: String,
    val targetLowWatts: Int,
    val targetHighWatts: Int,
)

data class DynSnapshot(
    val ifNp: DynIfNp,
    val temp: DynTemp,
    val wind: DynWind,
    val avg: DynAvg,
    val dist: DynDist,
    val eta: DynEta,
)

data class DynIfNp(val line1: String, val line2: String)
data class DynTemp(val value: String, val state: String)
data class DynWind(val icon: String, val speed: Double)
data class DynAvg(val net: String, val gross: String)
data class DynDist(val done: String, val left: String)
data class DynEta(val value: String)

data class RideMessageSnapshot(
    val type: String,
    val module: String,
    val severity: String,
    val line1: String,
    val line2: String,
)

/**
 * RideStateSnapshot — per-tick processed engine state.
 *
 * Used for timeline export and debugging.
 * Source of truth: QLab RideStateSnapshot in ~/Projects/QLab/web/lab.js
 */
data class RideStateSnapshot(
    val tick: Int,
    val timestampMs: Long,
    val elapsedSec: Long,
    val movingSec: Long,
    val distanceKm: Double,
    val speedKph: Double,
    val powerWatts: Int,
    val heartRate: Int,
    val cadenceRpm: Int,
    val gradePercent: Double,
    val npWatts: Int,
    val ifValue: Double,
    val viValue: Double,
    val tssValue: Double,
    val caloriesKcal: Int,
    val decouplingPercent: Double?,
    val carbsGPerH: Int,
    val fluidLPerH: Double,
    val rideReservePercent: Int,
    val wBalPercent: Int,
    val batteryPercent: Int,
    val batteryDropPerHour: Double,
    val batteryRuntimeSec: Long,
    val rearDerailleurBatteryPercent: Int?,
)

/**
 * QaIssue — portable QA finding.
 *
 * Source of truth: QLab QaIssue in ~/Projects/QLab/web/lab.js
 */
data class QaIssue(
    val level: String,
    val msg: String,
    val module: String,
    val tick: Int,
)

/**
 * Output envelope for the full replay log.
 */
data class ReplayLogEnvelope(
    val meta: ReplayLogMeta,
    val ticks: List<ReplayTick>,
)

data class ReplayLogMeta(
    val sourceFile: String,
    val totalTicks: Int,
    val totalDistanceKm: Double,
    val totalElapsedSec: Long,
    val totalMovingSec: Long,
    val hasPowerData: Boolean,
    val hasHeartRateData: Boolean,
    val hasCadenceData: Boolean,
    val exportedAt: String,
)

/**
 * Output envelope for the optional summary.
 */
data class ReplaySummary(
    val sourceFile: String,
    val totalTicks: Int,
    val totalDistanceKm: Double,
    val totalElapsedSec: Long,
    val totalMovingSec: Long,
    val avgPowerWatts: Int,
    val npWatts: Int,
    val ifValue: Double,
    val viValue: Double,
    val tssValue: Double,
    val caloriesKcal: Int,
    val rideReservePercent: Int,
    val wBalPercent: Int,
    val maxPowerWatts: Int,
    val maxHeartRate: Int,
    val avgHeartRate: Int,
    val avgSpeedKph: Double,
    val hasGearData: Boolean,
    val exportedAt: String,
)
