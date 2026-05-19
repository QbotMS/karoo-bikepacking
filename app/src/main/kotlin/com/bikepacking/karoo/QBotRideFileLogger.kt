package com.bikepacking.karoo

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.BufferedWriter
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Ride File Logger — zapisuje snapshoty jazdy do pliku JSONL na Karoo.
 *
 * - katalog: qbot_logs (app-specific external files dir)
 * - nazwa: qbot_ride_yyyy-MM-dd_HHmmss.jsonl
 * - jedna linia = jeden snapshot
 * - logowanie co 2 sekundy
 * - działa niezależnie od logcat
 * - nigdy nie crashuje ride
 */
class QBotRideFileLogger(private val context: Context) {

    private var writer: BufferedWriter? = null
    private var logFile: File? = null
    private var lastLogAtMs: Long = 0L
    private var isRunning = false

    companion object {
        private const val TAG = "QBOT_FILE_LOG"
        private const val LOG_INTERVAL_MS = 2_000L
    }

    /**
     * Start loggera — tworzy katalog i plik JSONL.
     * Zwraca ścieżkę pliku lub null jeśli nie udało się utworzyć.
     * Guard przed podwójnym startem.
     */
    fun start(): String? {
        if (isRunning) {
            Log.w(TAG, "Logger already running, ignoring duplicate start")
            return logFile?.absolutePath
        }
        try {
            val logsDir = File(context.getExternalFilesDir(null), "qbot_logs")
            if (!logsDir.exists()) {
                val created = logsDir.mkdirs()
                if (!created) {
                    Log.w(TAG, "Failed to create logs dir: ${logsDir.absolutePath}")
                    return null
                }
            }

            val timestamp = SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.US).format(System.currentTimeMillis())
            val fileName = "qbot_ride_$timestamp.jsonl"
            logFile = File(logsDir, fileName)

            writer = logFile!!.bufferedWriter()
            lastLogAtMs = 0L
            isRunning = true

            // Write logger_start event to JSONL
            val startEvent = JSONObject().apply {
                put("event", "logger_start")
                put("ts", System.currentTimeMillis())
                put("file", logFile!!.absolutePath)
            }
            writer?.write(startEvent.toString())
            writer?.newLine()
            writer?.flush()

            Log.d(TAG, "Logger started: ${logFile!!.absolutePath}")
            return logFile!!.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start logger", e)
            return null
        }
    }

    /**
     * Stop loggera — zapisuje logger_stop event i zamyka writer.
     */
    fun stop() {
        if (!isRunning) return
        try {
            // Write logger_stop event to JSONL
            val stopEvent = JSONObject().apply {
                put("event", "logger_stop")
                put("ts", System.currentTimeMillis())
                put("file", logFile?.absolutePath ?: "unknown")
            }
            writer?.write(stopEvent.toString())
            writer?.newLine()

            writer?.flush()
            writer?.close()
            writer = null
            isRunning = false
            Log.d(TAG, "Logger stopped: ${logFile?.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop logger", e)
        }
    }

    /**
     * Tick — wywoływany co sekundę z RideEngine.
     * Zapisuje snapshot co 2 sekundy.
     */
    fun tick(
        state: RideState,
        speedState: String?,
        powerState: String?,
        powerReason: String?,
        hrState: String?,
        cadenceState: String?,
        gradeState: String?,
        gearState: String?,
        gearReason: String?,
        climbActive: Boolean?,
        climbIndex: Int?,
        climbCount: Int?,
        distanceToTopM: Int?,
        ascentLeftM: Int?,
        avgGradePct: Float?,
        freshness: com.bikepacking.karoo.FreshnessTracker? = null,
        hrdStatus: String? = null,
        hrdPct: Float? = null,
        hrdPhase: String? = null,
        hrdValid: Boolean? = null,
        hrdReason: String? = null,
    ) {
        val now = System.currentTimeMillis()
        if (now - lastLogAtMs < LOG_INTERVAL_MS) return
        if (writer == null) return

        lastLogAtMs = now

        try {
            val snapshot = JSONObject().apply {
                put("event", "ride_tick")
                put("ts", now)
                put("elapsedSec", state.elapsedSec)

                // LIVE fields with freshness
                put("live", JSONObject().apply {
                    put("speed", buildLiveFieldJson(
                        value = state.speedKph, unit = "km/h", state = speedState,
                        freshness = freshness?.getFreshness("speed", now), ageMs = freshness?.getAgeMs("speed", now)
                    ))
                    put("power", buildLiveFieldJson(
                        value = state.powerWatts, unit = "W", state = powerState, reason = powerReason,
                        freshness = freshness?.getFreshness("power", now), ageMs = freshness?.getAgeMs("power", now)
                    ))
                    put("hr", buildLiveFieldJson(
                        value = state.heartRate, unit = "bpm", state = hrState,
                        freshness = freshness?.getFreshness("hr", now), ageMs = freshness?.getAgeMs("hr", now)
                    ))
                    put("cadence", buildLiveFieldJson(
                        value = state.cadenceRpm, unit = "rpm", state = cadenceState,
                        freshness = freshness?.getFreshness("cadence", now), ageMs = freshness?.getAgeMs("cadence", now)
                    ))
                    put("grade", buildLiveFieldJson(
                        value = state.gradePercent, unit = "%", state = gradeState,
                        freshness = freshness?.getFreshness("grade", now), ageMs = freshness?.getAgeMs("grade", now)
                    ))
                    put("gear", buildLiveFieldJson(
                        value = if (state.frontTeeth > 0 && state.rearTeeth > 0)
                            "${state.frontTeeth}x${state.rearTeeth}" else "--",
                        state = gearState, reason = gearReason,
                        freshness = freshness?.getFreshness("gear", now), ageMs = freshness?.getAgeMs("gear", now)
                    ))
                })

                // DYN fields
                put("dyn", JSONObject().apply {
                    put("D", state.distanceKm)
                    put("IF10", state.if10Value)
                    put("HRD", hrdStatus)
                    put("HRD_pct", hrdPct)
                    put("HRD_phase", hrdPhase)
                    put("HRD_valid", hrdValid)
                    put("HRD_reason", hrdReason)
                    put("WBal", state.wBalancePercent)
                    put("DTD", if (state.hasRoute && state.remainingKm > 0f) state.remainingKm else null)
                    put("avgSpeedNet", state.smartAvgNetKph)
                    put("temp", state.temperatureCelsius)
                    put("windText", if (state.windSpeedMs > 0f) "${state.windArrow} ${state.windSpeedMs}" else null)
                    put("windStatus", if (state.headwindError != 0) "error_${state.headwindError}" else "ok")
                })

                // STATS fields
                put("stats", JSONObject().apply {
                    put("movingSec", state.movingSec)
                    put("stoppedSec", maxOf(0L, state.elapsedSec - state.movingSec))
                    put("carbsGPerH", state.carbsGPerH)
                    put("fluidLPerH", state.fluidLPerH)
                    put("np", state.npWholeWatts)
                    put("if", state.ifWholeRide)
                    put("vi", state.viValue)
                    put("tss", state.tssValue)
                    put("kcal", state.caloriesKcal)
                    put("drift", state.decouplingPercent)
                    put("rsrv", state.rideReservePercent)
                    put("eta", if (state.etaTimestamp > 0L) state.etaTimestamp else null)
                    put("timeToStop", if (state.timeToFinishSec > 0L) state.timeToFinishSec else null)
                    put("ascentDone", state.ascentDoneM)
                    put("ascentLeft", state.ascentLeftM)
                    put("battery", state.batteryPercent)
                    put("rdBattery", state.rearDerailleurBatteryPercent)
                    // ΔV deadline delta
                    put("deadlineDeltaKph", if (state.deadlineDeltaKph != 0f || state.deadlineStatus != "--") state.deadlineDeltaKph else null)
                    put("deadlineStatus", if (state.deadlineStatus != "--") state.deadlineStatus else null)
                })

                // Deadline diagnostic section
                put("deadline", JSONObject().apply {
                    val hasRoute = state.hasRoute && state.remainingKm > 0f
                    val hasEta = state.etaTimestamp > 0L
                    val hasDeadline = state.deadlineTimestamp > 0L
                    val deadlineMode = if (hasDeadline && state.civilTwilightTimestamp > 0L && state.deadlineTimestamp == state.civilTwilightTimestamp) "CIVIL_DUSK" else "MANUAL"

                    put("mode", if (hasDeadline) deadlineMode else null)
                    put("deadlineTimeMs", if (hasDeadline) state.deadlineTimestamp else null)
                    put("etaTimeMs", if (hasEta) state.etaTimestamp else null)
                    put("distanceRemainingM", if (hasRoute) (state.remainingKm * 1000).toDouble() else null)
                    put("requiredSpeedKph", if (state.requiredSpeedKph > 0f) state.requiredSpeedKph else null)
                    put("currentEffectiveSpeedKph", if (state.smartAvgNetKph > 0f) state.smartAvgNetKph else null)
                    put("deltaSpeedKph", if (state.deadlineDeltaKph != 0f) state.deadlineDeltaKph else null)

                    // Determine status and reason
                    val (status, reason) = when {
                        !hasDeadline -> Pair("NO_DEADLINE", "NO_DEADLINE_SET")
                        !hasRoute -> Pair("NO_ROUTE", "NO_ROUTE_OR_DISTANCE")
                        !hasEta -> Pair("NO_ETA", "NO_ETA_CALCULATED")
                        state.deadlineTimestamp <= System.currentTimeMillis() -> Pair("LATE", "DEADLINE_PASSED")
                        state.etaTimestamp > state.deadlineTimestamp -> Pair("NEED_SPEED", "ETA_AFTER_DEADLINE")
                        state.deadlineDeltaKph <= 0f -> Pair("OK", "ETA_BEFORE_DEADLINE")
                        state.deadlineDeltaKph > 99.9f -> Pair("IMPOSSIBLE", "REQUIRED_SPEED_TOO_HIGH")
                        else -> Pair("NEED_SPEED", "ETA_AFTER_DEADLINE")
                    }
                    put("status", status)
                    put("reason", reason)
                })

                // Climb fields
                put("climb", JSONObject().apply {
                    put("active", climbActive ?: false)
                    put("climbIndex", climbIndex ?: 0)
                    put("climbCount", climbCount ?: 0)
                    put("distanceToTopM", distanceToTopM ?: 0)
                    put("ascentLeftM", ascentLeftM ?: 0)
                    put("avgGradePct", avgGradePct ?: 0f)
                })

                // Message
                put("message", state.activeRideMessage?.let { msg ->
                    JSONObject().apply {
                        put("type", msg.type)
                        put("severity", msg.severity.name)
                        put("line1", msg.line1)
                        put("line2", msg.line2)
                    }
                } ?: JSONObject.NULL)
            }

            writer?.write(snapshot.toString())
            writer?.newLine()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write snapshot", e)
        }
    }

    /**
     * Build JSON for a LIVE field with freshness info.
     * For MISSING: value=null, display="--", freshness="MISSING"
     * For FRESH/STALE: value=present, freshness="FRESH"/"STALE", ageMs=N
     */
    private fun buildLiveFieldJson(
        value: Any?,
        unit: String? = null,
        state: String?,
        reason: String? = null,
        freshness: com.bikepacking.karoo.DataFreshness?,
        ageMs: Long?,
    ): JSONObject {
        return JSONObject().apply {
            if (freshness == com.bikepacking.karoo.DataFreshness.MISSING) {
                put("value", null)
                put("display", "--")
                put("freshness", "MISSING")
                put("ageMs", ageMs ?: -1L)
            } else {
                put("value", value)
                if (unit != null) put("unit", unit)
                if (state != null) put("state", state)
                if (reason != null) put("reason", reason)
                put("freshness", freshness?.name ?: "UNKNOWN")
                put("ageMs", ageMs ?: -1L)
            }
        }
    }
}
