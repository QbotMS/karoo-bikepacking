package com.bikepacking.karoo.field

import com.bikepacking.karoo.HrZone
import com.bikepacking.karoo.PowerZone

enum class FieldState { GOOD, OK, WARN, BAD, NEUTRAL }

object FieldClassifier {

    private var lastHrState = FieldState.NEUTRAL
    private var hrStateSinceMs = 0L
    private var hrInitialized = false

    private var lastGearState = FieldState.NEUTRAL
    private var gearStateSinceMs = 0L
    private var gearInitialized = false

    private const val HR_HYSTERESIS_MS = 15_000L
    private const val GEAR_HYSTERESIS_MS = 30_000L

    fun resetHysteresis() {
        lastHrState = FieldState.NEUTRAL
        hrStateSinceMs = 0L
        hrInitialized = false
        lastGearState = FieldState.NEUTRAL
        gearStateSinceMs = 0L
        gearInitialized = false
    }

    fun speed(ctx: RideContext): FieldState {
        val speed = ctx.rider.speedKph
        val dist = ctx.rider.distanceKm
        val movingSec = ctx.effort.movingSec
        val netAvg = if (movingSec > 0L) dist / (movingSec / 3600f) else 0f
        if (netAvg < 1f) return FieldState.NEUTRAL
        return when {
            speed > netAvg * 1.15f -> FieldState.GOOD
            speed < netAvg * 0.85f -> FieldState.BAD
            else -> FieldState.OK
        }
    }

    fun power(ctx: RideContext): FieldState =
        PowerAdvisor.assess(ctx).state

    fun hr(bpm: Int, maxHr: Int, nowMs: Long = -1): FieldState {
        if (maxHr <= 0 || bpm <= 0) {
            lastHrState = FieldState.NEUTRAL
            return FieldState.NEUTRAL
        }
        val zone = HrZone.fromHr(bpm, maxHr)
        val raw = when (zone) {
            HrZone.Z2 -> FieldState.GOOD
            HrZone.Z1, HrZone.Z3 -> FieldState.OK
            HrZone.Z4 -> FieldState.WARN
            HrZone.Z5 -> FieldState.BAD
            HrZone.UNKNOWN -> FieldState.NEUTRAL
        }

        if (nowMs < 0) return raw

        if (!hrInitialized) {
            lastHrState = raw
            hrStateSinceMs = nowMs
            hrInitialized = true
            return raw
        }

        if (raw == lastHrState) {
            hrStateSinceMs = nowMs
            return raw
        }

        if (nowMs - hrStateSinceMs < HR_HYSTERESIS_MS) return lastHrState

        lastHrState = raw
        hrStateSinceMs = nowMs
        return raw
    }

    fun cadence(rpm: Int, gradePercent: Float = 0f): FieldState {
        if (rpm <= 0) return FieldState.NEUTRAL
        val targetLow: Int
        val targetHigh: Int
        when {
            gradePercent > 4f -> { targetLow = 55; targetHigh = 65 }
            gradePercent < -4f -> { targetLow = 65; targetHigh = 75 }
            else -> { targetLow = 60; targetHigh = 70 }
        }
        val okLow = targetLow - 5
        val okHigh = targetHigh + 7
        return when {
            rpm in targetLow..targetHigh -> FieldState.GOOD
            rpm in okLow until targetLow || rpm in (targetHigh + 1)..okHigh -> FieldState.OK
            else -> FieldState.BAD
        }
    }

    fun grade(percent: Float): FieldState {
        return when {
            percent in -2f..2f -> FieldState.GOOD
            percent in 2f..5f || percent in -5f..-2f -> FieldState.OK
            percent in 5f..9f || percent < -5f -> FieldState.WARN
            else -> FieldState.BAD
        }
    }

    fun gear(ctx: RideContext, nowMs: Long = -1): FieldState {
        val result = GearAdvisor.assess(ctx)
        val raw = result.state

        if (nowMs < 0) return raw

        if (!gearInitialized) {
            lastGearState = raw
            gearStateSinceMs = nowMs
            gearInitialized = true
            return raw
        }

        if (raw == lastGearState) {
            gearStateSinceMs = nowMs
            return raw
        }

        if (nowMs - gearStateSinceMs < GEAR_HYSTERESIS_MS) return lastGearState

        lastGearState = raw
        gearStateSinceMs = nowMs
        return raw
    }

    @Deprecated("Use gear(ctx, nowMs) instead")
    fun gear(
        cadenceRpm: Int,
        powerWatts: Int,
        gradePercent: Float,
        ftp: Int,
        nowMs: Long = -1
    ): FieldState = gear(
        RideContext(
            rider = RiderState(
                speedKph = 0f, powerWatts = powerWatts, heartRate = 0,
                cadenceRpm = cadenceRpm, gradePercent = gradePercent,
                frontTeeth = 40, rearTeeth = 15, distanceKm = 0f,
                smoothedPowerWatts = powerWatts,
                smoothedHeartRate = 0,
                smoothedCadenceRpm = cadenceRpm,
                smoothedGradePercent = gradePercent,
            ),
            route = RouteContext(0f, 0, 0L, false),
            effort = EffortContext(-1, 0f, 0L, 0L, null),
            gearCtx = GearContext(40, 15),
            todayFactor = 1.0f,
            ftp = ftp,
            maxHr = 0,
        ),
        nowMs,
    )
}
