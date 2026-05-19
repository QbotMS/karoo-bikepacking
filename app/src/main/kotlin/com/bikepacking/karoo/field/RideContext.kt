package com.bikepacking.karoo.field

import com.bikepacking.karoo.RideState

data class RiderState(
    val speedKph: Float,
    val powerWatts: Int,
    val heartRate: Int,
    val cadenceRpm: Int,
    val gradePercent: Float,
    val frontTeeth: Int,
    val rearTeeth: Int,
    val distanceKm: Float,
    val smoothedPowerWatts: Int,
    val smoothedHeartRate: Int,
    val smoothedCadenceRpm: Int,
    val smoothedGradePercent: Float,
)

data class RouteContext(
    val remainingKm: Float,
    val ascentLeftM: Int,
    val timeToFinishSec: Long,
    val hasRoute: Boolean,
)

data class EffortContext(
    val wBalancePercent: Int,
    val decouplingPercent: Float,
    val elapsedSec: Long,
    val movingSec: Long,
    val temperatureCelsius: Float?,
)

data class GearContext(
    val frontTeeth: Int,
    val rearTeeth: Int,
)

data class RideContext(
    val rider: RiderState,
    val route: RouteContext,
    val effort: EffortContext,
    val gearCtx: GearContext,
    val todayFactor: Float,
    val ftp: Int,
    val maxHr: Int,
) {
    companion object {
        fun from(
            state: RideState,
            smoothedPower: Int,
            smoothedHr: Int,
            smoothedCad: Int,
            smoothedGrade: Float,
            todayFactor: Float,
            ftp: Int,
            maxHr: Int,
        ): RideContext = RideContext(
            rider = RiderState(
                speedKph = state.speedKph,
                powerWatts = state.powerWatts,
                heartRate = state.heartRate,
                cadenceRpm = state.cadenceRpm,
                gradePercent = state.gradePercent,
                frontTeeth = state.frontTeeth,
                rearTeeth = state.rearTeeth,
                distanceKm = state.distanceKm,
                smoothedPowerWatts = smoothedPower,
                smoothedHeartRate = smoothedHr,
                smoothedCadenceRpm = smoothedCad,
                smoothedGradePercent = smoothedGrade,
            ),
            route = RouteContext(
                remainingKm = state.remainingKm,
                ascentLeftM = state.ascentLeftM,
                timeToFinishSec = state.timeToFinishSec,
                hasRoute = state.hasRoute,
            ),
            effort = EffortContext(
                wBalancePercent = state.wBalancePercent,
                decouplingPercent = state.decouplingPercent,
                elapsedSec = state.elapsedSec,
                movingSec = state.movingSec,
                temperatureCelsius = state.temperatureCelsius,
            ),
            gearCtx = GearContext(
                frontTeeth = state.frontTeeth,
                rearTeeth = state.rearTeeth,
            ),
            todayFactor = todayFactor,
            ftp = ftp,
            maxHr = maxHr,
        )
    }
}
