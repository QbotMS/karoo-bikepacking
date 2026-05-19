package com.bikepacking.karoo.message

import com.bikepacking.karoo.field.ClimbContext

class RideMessageCoordinator(
    private val twilightProvider: LightTwilightMessageProvider,
    private val climbPacingProvider: ClimbPacingMessageProvider,
    private val priorityEngine: MessagePriorityEngine = MessagePriorityEngine(),
    private val config: DynamicMessagesConfig = DynamicMessagesConfig(),
    private val debugProvider: DebugRideMessageProvider = DebugRideMessageProvider(config),
) {
    fun selectMessage(
        nowMs: Long,
        climbContext: ClimbContext,
        routeAscentRemainingM: Double? = null,
        latitude: Double = 0.0,
        longitude: Double = 0.0,
        etaMs: Long? = null,
        hasRoute: Boolean = false,
        remainingKm: Float? = null,
        isRiding: Boolean = false,
        ftpW: Int = 0,
        todayFactor: Float = 1.0f,
        wBalPercent: Int? = null,
        hrDriftPercent: Float? = null,
        routeDistanceRemainingKm: Float? = null,
        currentPowerW: Int = 0,
    ): RideMessage? {
        val candidates = mutableListOf<RideMessage>()

        if (config.debugMessageMode != DebugMessageMode.OFF) {
            debugProvider.generate(nowMs)?.let { candidates.add(it) }
        } else if (config.enabled) {
            candidates.addAll(twilightProvider.generate(
                nowMs = nowMs,
                latitude = latitude,
                longitude = longitude,
                etaMs = etaMs,
                hasRoute = hasRoute,
                remainingKm = remainingKm,
                isRiding = isRiding,
            ))

            candidates.addAll(climbPacingProvider.generate(
                climbContext = climbContext,
                nowMs = nowMs,
                routeAscentRemainingM = routeAscentRemainingM,
                ftpW = ftpW,
                todayFactor = todayFactor,
                wBalPercent = wBalPercent,
                hrDriftPercent = hrDriftPercent,
                routeDistanceRemainingKm = routeDistanceRemainingKm,
                currentPowerW = currentPowerW,
            ))
        }

        return priorityEngine.select(candidates, nowMs)
    }

    fun currentActiveMessage(): RideMessage? = priorityEngine.currentActiveMessage()
    fun dismissCurrent() = priorityEngine.dismissCurrent()
}
