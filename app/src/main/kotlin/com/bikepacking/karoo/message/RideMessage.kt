package com.bikepacking.karoo.message

enum class MessageSeverity(val level: Int) {
    INFO(0),
    WARNING(1),
    ALARM(2),
    CRITICAL(3)
}

enum class RideMessageModule {
    CLIMB_PACING,
    ROUTE_HORIZON,
    RIDE_TREND,
    FINISH_ROUTE_RISK,
    INTAKE,
    LIGHT_TWILIGHT,
    STOP_PAUSE,
    WEATHER_SHIFT,
    CALIBRATION_SETUP,
    SENSOR_DATA,
    BATTERY_DEVICE
}

data class RideMessage(
    val type: String,
    val module: RideMessageModule,
    val severity: MessageSeverity,
    val priority: Int = 0,
    val line1: String,
    val line2: String = "",
    val minDisplayMs: Long = 5000L,
    val cooldownMs: Long = 30000L,
    val createdAtMs: Long = System.currentTimeMillis()
)
