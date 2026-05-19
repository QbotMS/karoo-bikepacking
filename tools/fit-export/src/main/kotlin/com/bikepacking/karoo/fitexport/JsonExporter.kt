package com.bikepacking.karoo.fitexport

import com.google.gson.GsonBuilder
import com.google.gson.ToNumberPolicy
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * JSON exporter for QLab replay logs.
 *
 * Output files:
 *   <ride_name>.qbot_replay_log.json   — full tick-by-tick data
 *   <ride_name>.qbot_replay_summary.json — optional summary
 */
class JsonExporter {

    private val gson = GsonBuilder()
        .setPrettyPrinting()
        .serializeNulls()
        .setObjectToNumberStrategy(ToNumberPolicy.DOUBLE)
        .disableHtmlEscaping()
        .create()

    private val isoFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME
        .withZone(ZoneId.systemDefault())

    /**
     * Export full replay log: each tick as a ReplayTick with embedded hudState.
     */
    fun exportReplayLog(
        ticks: List<ReplayTick>,
        sourceFile: String,
        session: FitDecoder.SessionInfo,
        outputDir: File,
        baseName: String,
    ): File {
        val totalDist = ticks.lastOrNull()?.distanceM ?: 0.0
        val firstTs = ticks.firstOrNull()?.timestampMs ?: 0L
        val lastTs = ticks.lastOrNull()?.timestampMs ?: 0L
        val totalElapsed = (lastTs - firstTs) / 1000

        val meta = ReplayLogMeta(
            sourceFile = sourceFile,
            totalTicks = ticks.size,
            totalDistanceKm = (totalDist / 1000.0).let { Math.round(it * 100.0) / 100.0 },
            totalElapsedSec = totalElapsed,
            totalMovingSec = totalElapsed, // approximate
            hasPowerData = session.hasPowerData,
            hasHeartRateData = session.hasHrData,
            hasCadenceData = session.hasCadenceData,
            exportedAt = isoFormatter.format(Instant.now()),
        )

        val envelope = ReplayLogEnvelope(meta = meta, ticks = ticks)

        val outFile = File(outputDir, "${baseName}.qbot_replay_log.json")
        outFile.writeText(gson.toJson(envelope))
        return outFile
    }

    /**
     * Export optional summary JSON for quick inspection.
     */
    fun exportSummary(
        ticks: List<ReplayTick>,
        engine: CliRideEngine,
        sourceFile: String,
        session: FitDecoder.SessionInfo,
        outputDir: File,
        baseName: String,
    ): File? {
        val avgPower = if (session.avgPower > 0) session.avgPower else engine.let {
            if (it.hasPowerData) {
                val pwrTicks = ticks.mapNotNull { it.powerW?.toDouble() }
                if (pwrTicks.isNotEmpty()) pwrTicks.average().roundToInt() else 0
            } else 0
        }
        val avgHr = if (session.avgHeartRate > 0) session.avgHeartRate else {
            val hrTicks = ticks.mapNotNull { it.heartRateBpm?.toDouble() }
            if (hrTicks.isNotEmpty()) hrTicks.average().roundToInt() else 0
        }
        val maxPwr = session.maxPower.coerceAtLeast(ticks.maxOfOrNull { it.powerW ?: 0 } ?: 0)
        val maxHr = session.maxHeartRate.coerceAtLeast(ticks.maxOfOrNull { it.heartRateBpm ?: 0 } ?: 0)

        val summary = ReplaySummary(
            sourceFile = sourceFile,
            totalTicks = ticks.size,
            totalDistanceKm = (ticks.lastOrNull()?.distanceM ?: 0.0) / 1000.0,
            totalElapsedSec = (ticks.lastOrNull()?.timestampMs ?: 0L) / 1000,
            totalMovingSec = engine.movingSec,
            avgPowerWatts = avgPower,
            npWatts = engine.npWatts,
            ifValue = engine.ifValue,
            viValue = engine.viValue,
            tssValue = Math.round(engine.tssValue).toInt(),
            caloriesKcal = engine.caloriesKcal,
            rideReservePercent = engine.rideReservePercent,
            wBalPercent = engine.wBalPercent,
            maxPowerWatts = maxPwr,
            maxHeartRate = maxHr,
            avgHeartRate = avgHr,
            avgSpeedKph = if (ticks.isNotEmpty()) {
                val speeds = ticks.mapNotNull { it.speedMps?.let { s -> s * 3.6 } }
                if (speeds.isNotEmpty()) Math.round(speeds.average() * 10.0) / 10.0 else 0.0
            } else 0.0,
            hasGearData = session.hasGearData,
            exportedAt = isoFormatter.format(Instant.now()),
        )

        val outFile = File(outputDir, "${baseName}.qbot_replay_summary.json")
        outFile.writeText(gson.toJson(summary))
        return outFile
    }

    private fun Double.roundToInt(): Int = Math.round(this).toInt()
}
