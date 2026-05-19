package com.bikepacking.karoo.fitexport

import com.garmin.fit.*
import java.io.File
import java.io.FileInputStream

/**
 * FIT file decoder using the Garmin FIT SDK.
 *
 * Extracts all record messages plus session/file-id metadata.
 * FIT records with missing timestamps are skipped.
 * All values are optional per QLab contract.
 */
class FitDecoder {

    data class FitResult(
        val ticks: List<ReplayTick>,
        val session: SessionInfo,
    )

    data class SessionInfo(
        val startTime: Long = 0L,
        val totalDistanceM: Double = 0.0,
        val totalElapsedSec: Long = 0L,
        val totalMovingSec: Long = 0L,
        val maxSpeedMps: Double = 0.0,
        val avgSpeedMps: Double = 0.0,
        val maxPower: Int = 0,
        val avgPower: Int = 0,
        val maxHeartRate: Int = 0,
        val avgHeartRate: Int = 0,
        val maxCadence: Int = 0,
        val avgCadence: Int = 0,
        val totalAscentM: Double = 0.0,
        val totalDescentM: Double = 0.0,
        val avgTemperature: Int = 0,
        val hasPowerData: Boolean = false,
        val hasHrData: Boolean = false,
        val hasCadenceData: Boolean = false,
        val hasGearData: Boolean = false,
    )

    fun decode(file: File): FitResult {
        val records = mutableListOf<ReplayTick>()
        val fileInputStream = FileInputStream(file)
        val decode = Decode()
        val mesgListener = FitMessageListener()

        try {
            decode.read(fileInputStream, mesgListener, mesgListener)
        } catch (e: Exception) {
            System.err.println("FIT decode warning for ${file.name}: ${e.message}")
        } finally {
            fileInputStream.close()
        }

        val mesgs = mesgListener.messages
        val recordMesgs = mesgs.records

        // Sort by timestamp just in case
        recordMesgs.sortBy { it.timestamp }

        var firstTimestamp: Long? = null
        var prevDistance: Double? = null
        var hasPowerData = false
        var hasHrData = false
        var hasCadenceData = false
        var hasGearData = false

        for (rm in recordMesgs) {
            val ts = rm.timestamp?.let { dateTimeToMillis(it) } ?: continue
            if (firstTimestamp == null) firstTimestamp = ts

            val distanceM = rm.distance?.toDouble()
            val speedMps = rm.speed?.toDouble()
            val powerW = rm.power?.toInt()?.takeIf { it > 0 }
            val hrBpm = rm.heartRate?.toInt()?.takeIf { it > 0 }
            val cadRpm = rm.cadence?.toInt()?.takeIf { it > 0 }
            val altM = rm.altitude?.toDouble()
            val tempC = rm.temperature?.toDouble()

            if (powerW != null) hasPowerData = true
            if (hrBpm != null) hasHrData = true
            if (cadRpm != null) hasCadenceData = true

            // Grade: compute from altitude and distance delta
            val gradePct = computeGrade(altM, distanceM, prevDistance)

            // Gear data from FIT event messages (front/rear shifts)
            val gear = extractGear(mesgs.events, mesgs, ts)

            if (gear != null && gear.frontTeeth != null && gear.rearTeeth != null) {
                hasGearData = true
            }

            prevDistance = distanceM

            val tick = ReplayTick(
                timestampMs = ts,
                distanceM = distanceM,
                speedMps = speedMps,
                powerW = powerW,
                heartRateBpm = hrBpm,
                cadenceRpm = cadRpm,
                gradePct = gradePct,
                altitudeM = altM,
                temperatureC = tempC,
                gear = gear,
                route = null,
                position = extractPosition(rm),
                wind = null,
            )
            records.add(tick)
        }

        // Build session info and attach data presence flags
        val sessionInfo = buildSessionInfo(mesgs.session, mesgs.fileId, records).let {
            it.copy(
                hasPowerData = hasPowerData,
                hasHrData = hasHrData,
                hasCadenceData = hasCadenceData,
                hasGearData = hasGearData,
            )
        }

        return FitResult(ticks = records, session = sessionInfo)
    }

    private fun dateTimeToMillis(dt: DateTime): Long = dt.timestamp * 1000L

    private fun computeGrade(
        altitudeM: Double?,
        distanceM: Double?,
        prevDistance: Double?,
    ): Double? {
        if (altitudeM == null || distanceM == null || prevDistance == null) return null
        val distDelta = distanceM - prevDistance
        if (distDelta < 0.5) return null  // too short to compute
        // Last stored altitude for grade calculation
        return null  // grade is computed in post-processing across ticks
    }

    /**
     * Extract gear data from event messages (gear shift events).
     * Falls back to computed gear if no events found.
     */
    private fun extractGear(
        eventMesgs: List<EventMesg>,
        mesgs: FitMessages,
        timestampMs: Long,
    ): GearInfo? {
        // Try to find gear shift events at or near this timestamp
        val tolerance = 2000L // 2 seconds tolerance

        // Front gear shifts are typically EventType.FRONT_GEAR_CHANGE
        // Rear gear shifts are typically EventType.REAR_GEAR_CHANGE
        // But the actual field names depend on the FIT SDK version.
        //
        // Common patterns:
        //   event.type = EventType.FRONT_GEAR_CHANGE or EventType.REAR_GEAR_CHANGE
        //   event.data = gear teeth count
        //   event.data1 = rear teeth
        //   event.data2 = front teeth

        var frontTeeth: Int? = null
        var rearTeeth: Int? = null

        for (em in eventMesgs) {
            val eventTs = em.timestamp?.let { dateTimeToMillis(it) } ?: continue
            if (kotlin.math.abs(eventTs - timestampMs) > tolerance) continue

            val eventType = em.event
            val eventData = em.data

            when (eventType) {
                EventType.FRONT_GEAR_CHANGE -> {
                    frontTeeth = eventData?.toInt()
                }
                EventType.REAR_GEAR_CHANGE -> {
                    rearTeeth = eventData?.toInt()
                }
                EventType.FRONT_GEAR_COUNT -> {
                    if (frontTeeth == null) frontTeeth = eventData?.toInt()
                }
                EventType.REAR_GEAR_COUNT -> {
                    if (rearTeeth == null) rearTeeth = eventData?.toInt()
                }
            }
        }

        // Also check shift mesgs if present
        for (sm in mesgs.shifts) {
            val shiftTs = sm.timestamp?.let { dateTimeToMillis(it) } ?: continue
            if (kotlin.math.abs(shiftTs - timestampMs) > tolerance) continue
            val front = sm.frontGearNum?.toInt()
            val rear = sm.rearGearNum?.toInt()
            if (front != null) frontTeeth = front
            if (rear != null) rearTeeth = rear
        }

        if (frontTeeth == null && rearTeeth == null) return null

        return GearInfo(
            frontTeeth = frontTeeth,
            rearTeeth = rearTeeth,
            sourceTrusted = true,
        )
    }

    private fun extractPosition(rm: RecordMesg): PositionInfo? {
        val lat = rm.positionLat?.let { semicirclesToDegrees(it) }
        val lon = rm.positionLong?.let { semicirclesToDegrees(it) }
        if (lat == null && lon == null) return null
        return PositionInfo(lat = lat, lon = lon)
    }

    private fun semicirclesToDegrees(sc: Int): Double = sc.toDouble() * 180.0 / 2_147_483_648.0

    private fun buildSessionInfo(
        sessionMesgs: List<SessionMesg>,
        fileIdMesgs: List<FileIdMesg>,
        records: List<ReplayTick>,
    ): SessionInfo {
        if (sessionMesgs.isNotEmpty()) {
            val sm = sessionMesgs.first()
            val elapsed = sm.totalTimerTime?.toLong()?.times(1000) ?: 0L
            return SessionInfo(
                startTime = sm.startTime?.let { dateTimeToMillis(it) } ?: records.firstOrNull()?.timestampMs ?: 0L,
                totalDistanceM = sm.totalDistance?.toDouble() ?: records.lastOrNull()?.distanceM ?: 0.0,
                totalElapsedSec = elapsed / 1000,
                totalMovingSec = sm.totalMovingTime?.toLong()?.div(1000) ?: elapsed / 1000,
                maxSpeedMps = sm.maxSpeed?.toDouble() ?: Double.NaN,
                avgSpeedMps = sm.avgSpeed?.toDouble() ?: Double.NaN,
                maxPower = sm.maxPower?.toInt() ?: 0,
                avgPower = sm.avgPower?.toInt() ?: 0,
                maxHeartRate = sm.maxHeartRate?.toInt() ?: 0,
                avgHeartRate = sm.avgHeartRate?.toInt() ?: 0,
                maxCadence = sm.maxCadence?.toInt() ?: 0,
                avgCadence = sm.avgCadence?.toInt() ?: 0,
                totalAscentM = sm.totalAscent?.toDouble() ?: 0.0,
                totalDescentM = sm.totalDescent?.toDouble() ?: 0.0,
                avgTemperature = sm.avgTemperature?.toInt() ?: 0,
            )
        }

        // Fallback: compute from records
        val firstTs = records.firstOrNull()?.timestampMs ?: 0L
        val lastTs = records.lastOrNull()?.timestampMs ?: 0L
        val lastDist = records.lastOrNull()?.distanceM ?: 0.0
        val maxPwr = records.maxOfOrNull { it.powerW ?: 0 } ?: 0
        val maxHr = records.maxOfOrNull { it.heartRateBpm ?: 0 } ?: 0

        return SessionInfo(
            startTime = firstTs,
            totalDistanceM = lastDist,
            totalElapsedSec = (lastTs - firstTs) / 1000,
            totalMovingSec = (lastTs - firstTs) / 1000,
            maxSpeedMps = Double.NaN,
            maxPower = maxPwr,
            maxHeartRate = maxHr,
            avgTemperature = 0,
        )
    }
}

/**
 * FIT SDK message collector.
 */
private class FitMessageListener : FitListener, MesgListener {
    val messages = FitMessages()

    override fun onMesg(mesg: Mesg) {
        when (mesg.num) {
            MesgNum.RECORD -> {
                val rm = RecordMesg(mesg)
                if (rm.timestamp != null) {
                    messages.records.add(rm)
                }
            }
            MesgNum.SESSION -> messages.session.add(SessionMesg(mesg))
            MesgNum.LAP -> messages.laps.add(LapMesg(mesg))
            MesgNum.EVENT -> messages.events.add(EventMesg(mesg))
            MesgNum.FILE_ID -> messages.fileId.add(FileIdMesg(mesg))
            MesgNum.SHIFT -> messages.shifts.add(ShiftMesg(mesg))
        }
    }

    override fun onMesg(mesg: Mesg, mesgIndex: MesgIndex?) = onMesg(mesg)
}

/**
 * Holds decoded FIT messages of interest.
 */
private class FitMessages {
    val records = mutableListOf<RecordMesg>()
    val session = mutableListOf<SessionMesg>()
    val laps = mutableListOf<LapMesg>()
    val events = mutableListOf<EventMesg>()
    val fileId = mutableListOf<FileIdMesg>()
    val shifts = mutableListOf<ShiftMesg>()
}


